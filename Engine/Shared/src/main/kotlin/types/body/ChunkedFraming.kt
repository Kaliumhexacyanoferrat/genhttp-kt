package org.genhttp.engine.shared.types.body

import org.genhttp.api.content.ProviderException
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.engine.shared.io.ConnectionReader
import org.genhttp.glyph11.HttpParseException
import org.genhttp.glyph11.parser.ChunkResult

import java.nio.ByteBuffer

import org.genhttp.glyph11.parser.ChunkedBodyStream as ChunkParser

/**
 * A body sent with `Transfer-Encoding: chunked`.
 *
 * A decoded chunk stays in the buffer of the reader until it has been handed out completely, so reading with small
 * buffers does not need to copy the remainder of a chunk anywhere else.
 */
internal class ChunkedFraming : BodyFraming {

    private var _reader: ConnectionReader? = null

    private var parser = ChunkParser()

    private var completed = false

    private var _position = 0L

    // the chunk that has been handed out partially (its payload stays in the buffer of the reader)
    private var holding = false

    // offset of the payload that has not been handed out yet, relative to the first unconsumed byte of the reader
    private var chunkOffset = 0

    private var chunkRemaining = 0

    // the length of the chunk (including its framing), consumed once the payload has been handed out
    private var chunkLength = 0

    private var expectation: ContinueReader? = null

    // region Get-/Setters

    override val length: Long?
        get() = null

    override val position: Long
        get() = _position

    private val reader: ConnectionReader
        get() = _reader ?: throw IllegalStateException("Framing has not been initialized")

    // endregion

    // region Initialization

    /**
     * @param expectation Sends the 100 (Continue) the client waits for before the body is read, if any
     */
    fun apply(reader: ConnectionReader, expectation: ContinueReader? = null) {
        _reader = reader
        this.expectation = expectation
        clear()
    }

    override fun reset() {
        _reader = null
        expectation = null
        clear()
    }

    private fun clear() {
        parser = ChunkParser()
        completed = false
        _position = 0
        holding = false
        chunkOffset = 0
        chunkRemaining = 0
        chunkLength = 0
    }

    // endregion

    // region Functionality

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) {
            return 0
        }

        if (holding) {
            return copyFromChunk(target, offset, length)
        }

        return readNext(target, offset, length)
    }

    private fun readNext(target: ByteArray, offset: Int, length: Int): Int {
        val reader = reader

        try {
            while (!completed) {
                expectation?.beforeRead()

                if (reader.available > 0) {
                    when (parser.tryReadChunk(reader.peek())) {
                        ChunkResult.Chunk -> {
                            val dataOffset = parser.dataOffset
                            val dataLength = parser.dataLength

                            if (dataLength <= length) {
                                return copyChunk(reader, dataOffset, dataLength, parser.bytesConsumed, target, offset)
                            }

                            holdChunk(dataOffset, dataLength, parser.bytesConsumed)
                            return copyFromChunk(target, offset, length)
                        }

                        ChunkResult.Completed -> {
                            reader.consume(parser.bytesConsumed)
                            completed = true
                            return 0
                        }

                        ChunkResult.NeedMoreData -> {
                            // wait for more data below
                        }
                    }
                }

                if (reader.fill() < 0) {
                    throw unexpectedEnd()
                }
            }

            return 0
        } catch (e: HttpParseException) {
            throw malformed(e)
        }
    }

    override fun readToEnd(): ByteBuffer {
        val collector = Collector()

        if (holding) {
            writeChunk(collector)
        }

        consume(collector)

        return collector.toBuffer()
    }

    override fun skip(): Boolean {
        releaseChunk()

        if (completed) {
            return true
        }

        return try {
            consume(null)
            true
        } catch (_: ProviderException) {
            false
        }
    }

    /**
     * Reads the remaining chunks, passing their data to the given collector (if any).
     */
    private fun consume(collector: Collector?) {
        val reader = reader

        while (!completed) {
            expectation?.beforeRead()

            consumeBuffered(reader, collector)

            if (completed) {
                return
            }

            if (reader.fill() < 0) {
                throw unexpectedEnd()
            }
        }
    }

    /**
     * Parses all complete chunks that have been buffered by the reader.
     */
    private fun consumeBuffered(reader: ConnectionReader, collector: Collector?) {
        try {
            while (reader.available > 0) {
                val buffered = reader.peek()

                when (parser.tryReadChunk(buffered)) {
                    ChunkResult.NeedMoreData -> return

                    ChunkResult.Completed -> {
                        reader.consume(parser.bytesConsumed)
                        completed = true
                        return
                    }

                    ChunkResult.Chunk -> {
                        if (collector != null) {
                            collector.write(buffered, buffered.position() + parser.dataOffset, parser.dataLength)

                            _position += parser.dataLength
                        }

                        reader.consume(parser.bytesConsumed)
                    }
                }
            }
        } catch (e: HttpParseException) {
            throw malformed(e)
        }
    }

    private fun copyChunk(reader: ConnectionReader, dataOffset: Int, dataLength: Int, consumed: Int, target: ByteArray, offset: Int): Int {
        val buffered = reader.peek()

        buffered.get(buffered.position() + dataOffset, target, offset, dataLength)

        reader.consume(consumed)

        _position += dataLength

        return dataLength
    }

    private fun holdChunk(dataOffset: Int, dataLength: Int, consumed: Int) {
        holding = true

        chunkOffset = dataOffset
        chunkRemaining = dataLength
        chunkLength = consumed
    }

    private fun copyFromChunk(target: ByteArray, offset: Int, length: Int): Int {
        val count = minOf(length, chunkRemaining)

        val buffered = reader.peek()

        buffered.get(buffered.position() + chunkOffset, target, offset, count)

        chunkOffset += count
        chunkRemaining -= count

        _position += count

        if (chunkRemaining == 0) {
            releaseChunk()
        }

        return count
    }

    private fun writeChunk(collector: Collector) {
        val buffered = reader.peek()

        collector.write(buffered, buffered.position() + chunkOffset, chunkRemaining)

        _position += chunkRemaining

        releaseChunk()
    }

    private fun releaseChunk() {
        if (holding) {
            reader.consume(chunkLength)

            holding = false

            chunkOffset = 0
            chunkRemaining = 0
            chunkLength = 0
        }
    }

    private fun unexpectedEnd(): ProviderException = ProviderException(ResponseStatus.BadRequest, "Unexpected end of chunked body")

    private fun malformed(e: HttpParseException): ProviderException =
        ProviderException(ResponseStatus.fromCode(e.statusCode) ?: ResponseStatus.BadRequest, "Failed to read chunked encoded body", e)

    // endregion

    /**
     * Collects the payload of the chunks into a growing array (`ArrayBufferWriter` upstream).
     */
    private class Collector {

        private var array = ByteArray(InitialCapacity)

        private var size = 0

        fun write(source: ByteBuffer, index: Int, length: Int) {
            if (size + length > array.size) {
                array = array.copyOf(maxOf(array.size * 2, size + length))
            }

            source.get(index, array, size, length)

            size += length
        }

        fun toBuffer(): ByteBuffer = ByteBuffer.wrap(array, 0, size).slice().asReadOnlyBuffer()

        private companion object {

            const val InitialCapacity = 1024

        }

    }

}
