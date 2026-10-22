package org.genhttp.modules.websockets.provider

import kotlinx.coroutines.runInterruptible

import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.ResponseSink
import org.genhttp.modules.websockets.ImperativeConnection
import org.genhttp.modules.websockets.ReactiveConnection
import org.genhttp.modules.websockets.WebsocketFrame
import org.genhttp.modules.websockets.protocol.Frame
import org.genhttp.modules.websockets.protocol.FrameDecodeResult
import org.genhttp.modules.websockets.protocol.FrameError
import org.genhttp.modules.websockets.protocol.FrameErrorType
import org.genhttp.modules.websockets.protocol.FrameType
import org.genhttp.modules.websockets.protocol.ParsedWebsocketFrame
import org.genhttp.modules.websockets.protocol.decode
import org.genhttp.modules.websockets.utils.SequenceUtils

import java.nio.ByteBuffer

/**
 * A websocket connection that reads frames from the input of an upgraded request and
 * writes frames to the sink of the response.
 *
 * The input is read into a buffer that is shared by all frames: the data of a frame is a view on this
 * buffer and therefore only valid until the next frame is read (unless the frame data is allocated, which is
 * the default). While a message that is split into multiple frames is assembled, the segments are kept valid.
 *
 * @param request The request that initially caused the websocket connection
 * @param sink The sink to write outgoing frames to
 * @param settings The settings of the connection
 */
public class WebsocketConnection(
    override val request: Request,
    sink: ResponseSink,
    override val settings: ConnectionSettings
) : ReactiveConnection, ImperativeConnection {

    private val writer = sink.writer

    private val stream = sink.stream

    private val input = request.upgrade()

    // The buffered input. The region [dataStart, dataEnd) is retained, [parseStart, dataEnd) has not been decoded yet.
    private var buffer = ByteArray(MaxFrameSize)

    // Start of the retained data. While a segmented message is being assembled, earlier frames are held as zero-copy
    // slices into the buffer, so this position must stay at the first segment until the message is complete.
    private var dataStart = 0

    // Start of the next frame to be decoded.
    private var parseStart = 0

    // The position up to which the buffered data has been examined, more data is needed to make progress after it.
    private var examinedEnd = 0

    private var dataEnd = 0

    // Whether the end of the input has been reached.
    private var completed = false

    // Whether slices of the buffer are referenced by a segmented message that is currently being assembled.
    private var segmentsReferenced = false

    private var skipFrameInit = false
    private var keepBufferValid = false
    private var pendingAdvance = false

    private lateinit var frame: ParsedWebsocketFrame

    // region Functionality

    override suspend fun write(payload: ByteBuffer, opcode: FrameType, fin: Boolean, flush: Boolean) {
        Frame.write(writer, payload, opcode = opcode.ordinal.toByte(), fin = fin)
        if (flush) stream.flush()
    }

    override suspend fun ping(flush: Boolean) {
        Frame.writePing(writer)
        if (flush) stream.flush()
    }

    override suspend fun pong(payload: ByteBuffer, flush: Boolean) {
        Frame.writePong(writer, payload)
        if (flush) stream.flush()
    }

    /**
     * Creates a pong frame with the given content and sends it to the client.
     *
     * @param payload The payload to be sent
     * @param flush false to buffer the frame without flushing; call [flush] when done to send buffered frames in one go
     */
    public suspend fun pong(payload: String, flush: Boolean = true) {
        pong(ByteBuffer.wrap(payload.toByteArray(Charsets.UTF_8)), flush)
    }

    override suspend fun pong(flush: Boolean) {
        pong(ByteBuffer.allocate(0), flush)
    }

    override suspend fun close(reason: String?, statusCode: Int, flush: Boolean) {
        Frame.writeClose(writer, reason, statusCode)
        if (flush) stream.flush()
    }

    override suspend fun flush() {
        stream.flush()
    }

    override suspend fun readFrame(): WebsocketFrame {
        advance()

        if (settings.handleContinuationFramesManually) {
            return readFrameSegment()
        }

        return readSegmentedFrame()
    }

    override fun tryReadFrame(): WebsocketFrame? {
        advance()

        if (dataEnd <= examinedEnd) {
            pullAvailable()

            if (dataEnd <= examinedEnd && !completed) {
                return null
            }
        }

        // Reached when the stream closed after a prior read already saw the end of the input.
        // Returning a close frame lets the caller's drain loop terminate naturally.
        if (dataStart == dataEnd && completed) {
            discardAll()
            return ParsedWebsocketFrame(this, FrameType.Close)
        }

        val result = decodeNext()

        val decoded = result.frame

        if (decoded.isError()?.errorType == FrameErrorType.Incomplete) {
            // Not a complete frame yet; keep all data and mark all as examined so
            // the next read waits for new data rather than returning immediately.
            examinedEnd = dataEnd
            return null
        }

        if (settings.allocateFrameData) {
            decoded.setCachedData()
        }

        pendingAdvance = true

        return decoded
    }

    private suspend fun readSegmentedFrame(): ParsedWebsocketFrame {
        var faulted = false

        try {
            if (!skipFrameInit) // when picking up an ongoing segmented frame after being interrupted by a ping/pong
            {
                frame = readFrameSegment()

                if (frame.fin) // hot path
                {
                    return frame
                }

                frame.isSegmented = true
                frame.segmentList = mutableListOf(frame.memory)

                segmentsReferenced = true
            } else {
                skipFrameInit = false
            }

            // cold path, segmented
            while (request.server.running) {
                // read the next frame
                val nextFrame = readFrameSegment()

                if (isErrorFrame(nextFrame)) {
                    // return the error frame, disregard all the possible segment frames collected so far
                    segmentsReferenced = false
                    return nextFrame
                }

                if (isControlFrame(nextFrame)) {
                    if (nextFrame.type == FrameType.Close) {
                        segmentsReferenced = false
                        return nextFrame
                    }

                    keepBufferValid = true
                    skipFrameInit = true
                    return nextFrame
                }

                val segments = frame.segmentList
                    ?: throw IllegalStateException("Existing frame does not contain segments to append to") // this SHOULD be impossible to happen

                segments.add(nextFrame.memory)

                // if the received frame is FIN=1, merge all the segments into the memory of the frame
                if (nextFrame.fin) {
                    frame.memory = SequenceUtils.concatSequences(segments)

                    segmentsReferenced = false
                    return frame
                }
            }

            segmentsReferenced = false

            return ParsedWebsocketFrame(this, FrameError("Unable to receive or assemble the segmented frame.", FrameErrorType.UndefinedBehavior))
        } catch (e: Throwable) {
            faulted = true
            segmentsReferenced = false
            throw e
        } finally {
            // a read that threw left the frame at the previous frame (or none), whose buffer is no longer ours
            if (settings.allocateFrameData && !faulted) {
                frame.setCachedData()
            }
        }
    }

    /**
     * Reads the next frame (or segment of a frame) from the connection.
     */
    private suspend fun readFrameSegment(): ParsedWebsocketFrame {
        while (request.server.running) {
            // wait for new data (unless there is data we did not look at yet or the input has ended)
            if (dataEnd <= examinedEnd && !completed) {
                fill()
            }

            if (dataStart == dataEnd && completed) // clean EOF: no more data, input completed
            {
                discardAll()
                return ParsedWebsocketFrame(this, FrameType.Close)
            }

            val decoded = decodeNext().frame

            // need more data for a complete frame
            if (decoded.isError()?.errorType == FrameErrorType.Incomplete) {
                if (completed) {
                    // there is no more data, so the partial data of the request is discarded
                    discardAll()
                    return ParsedWebsocketFrame(this, FrameError(FrameError.UnexpectedEndOfStream, FrameErrorType.IncompleteForever))
                }

                // mark the data as examined to ensure new data is read in case of an incomplete frame
                examinedEnd = dataEnd

                continue // read more data
            }

            // any other frame (including other errors)
            pendingAdvance = true
            return decoded
        }

        throw IllegalStateException("Server has been stopped")
    }

    /**
     * Decodes the next frame from the buffered data and updates the positions accordingly.
     */
    private fun decodeNext(): FrameDecodeResult {
        val result = Frame.decode(this, ByteBuffer.wrap(buffer, parseStart, dataEnd - parseStart), MaxFrameSize)

        val start = parseStart

        parseStart = start + result.consumed
        examinedEnd = start + result.examined

        return result
    }

    /**
     * Reads more data from the connection, suspending until at least one byte is available.
     */
    private suspend fun fill() {
        prepareSpace()

        val read = runInterruptible { input.read(buffer, dataEnd, buffer.size - dataEnd) }

        if (read < 0) {
            completed = true
        } else {
            dataEnd += read
        }
    }

    /**
     * Reads the data that is already available without waiting for more data.
     */
    private fun pullAvailable() {
        val available = input.available()

        if (available <= 0) {
            return
        }

        prepareSpace()

        val read = input.read(buffer, dataEnd, minOf(available, buffer.size - dataEnd))

        if (read < 0) {
            completed = true
        } else {
            dataEnd += read
        }
    }

    /**
     * Makes sure there is room in the buffer to read additional data.
     *
     * The buffer is only compacted if no segments reference it, otherwise the retained data is moved
     * into a new array (the segments keep referencing the old one).
     */
    private fun prepareSpace() {
        if (!segmentsReferenced && dataStart == dataEnd && dataStart > 0) {
            shift(dataStart, buffer)
        }

        if (dataEnd < buffer.size) {
            return
        }

        if (dataStart > 0) {
            shift(dataStart, if (segmentsReferenced) ByteArray(buffer.size) else buffer)
        } else {
            shift(0, ByteArray(buffer.size * 2))
        }
    }

    private fun shift(offset: Int, target: ByteArray) {
        System.arraycopy(buffer, dataStart, target, 0, dataEnd - dataStart)

        buffer = target

        parseStart -= offset
        examinedEnd -= offset
        dataEnd -= offset
        dataStart -= offset
    }

    private fun discardAll() {
        dataStart = dataEnd
        parseStart = dataEnd
        examinedEnd = dataEnd
    }

    private fun advance() {
        if (!pendingAdvance) return

        pendingAdvance = false

        if (keepBufferValid) {
            // an interrupted segmented message is continued, so the data of its segments has to be retained
            keepBufferValid = false
            return
        }

        // consume the data of the previous frame
        dataStart = parseStart
    }

    // endregion

    // region Helpers

    private fun isErrorFrame(frame: ParsedWebsocketFrame) = frame.type == FrameType.Error

    private fun isControlFrame(frame: ParsedWebsocketFrame) = when (frame.type) {
        FrameType.Close -> true
        FrameType.Ping -> true
        FrameType.Pong -> true
        else -> false
    }

    // endregion

    private companion object {

        private const val MaxFrameSize = 16 * 1024

    }

}
