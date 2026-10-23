package org.genhttp.engine.netty.protocol

import org.genhttp.engine.shared.io.ConnectionReader
import org.genhttp.glyph11.HttpParseException
import org.genhttp.glyph11.parser.ChunkResult
import org.genhttp.glyph11.protocol.BinaryRequest
import org.genhttp.glyph11.validation.BodyFraming
import org.genhttp.glyph11.validation.BodyFramingDetector
import org.genhttp.glyph11.validation.BodyFramingResult

import org.genhttp.glyph11.parser.ChunkedBodyStream as ChunkParser

/**
 * Checks whether the body of a request has been received completely.
 *
 * Bodies are buffered before the handler is invoked, as the stream returned by `RequestBody.asStream()` blocks the
 * caller and the data can only arrive through the event loop the handler runs on. Bodies of known length are
 * complete as soon as enough data is buffered, chunked bodies as soon as the terminating chunk and the trailers have
 * been scanned. Malformed chunks are left to the body stream, which reports the error to the handler.
 *
 * The state is kept while the connection waits for more data and needs to be [reset] for the next request.
 */
internal class BodyBuffering {

    private var framing: BodyFramingResult? = null

    private var chunks: ChunkParser? = null

    private var scanned = 0

    /**
     * Whether the body of the request that has just been parsed (with a header of the given length at the start of
     * the buffered data) is buffered completely.
     *
     * @throws HttpParseException if the body is too large to be buffered
     */
    fun isComplete(request: BinaryRequest, reader: ConnectionReader, headerLength: Int): Boolean {
        val detected = framing ?: BodyFramingDetector.detectBodyFraming(request).also { framing = it }

        return when (detected.framing) {
            BodyFraming.ContentLength -> {
                if (detected.contentLength > MaxBodySize) {
                    throw TooLarge()
                }

                reader.available - headerLength >= detected.contentLength
            }

            BodyFraming.Chunked -> scan(reader, headerLength)

            else -> true
        }
    }

    private fun scan(reader: ConnectionReader, headerLength: Int): Boolean {
        val parser = chunks ?: ChunkParser().also { chunks = it }

        val data = reader.data()

        while (true) {
            data.position(headerLength + scanned)

            val result = try {
                parser.tryReadChunk(data)
            } catch (_: HttpParseException) {
                return true
            }

            when (result) {
                ChunkResult.Chunk -> scanned += parser.bytesConsumed

                ChunkResult.Completed -> return true

                ChunkResult.NeedMoreData -> {
                    if (data.limit() - headerLength > MaxBodySize) {
                        throw TooLarge()
                    }

                    return false
                }
            }
        }
    }

    /**
     * Resets the state for the next request.
     */
    fun reset() {
        framing = null
        chunks = null
        scanned = 0
    }

    private class TooLarge : HttpParseException("Request body is too large to be buffered.", 413)

    private companion object {

        /**
         * The largest body that is buffered (the buffer of the reader cannot grow beyond 1 GB).
         */
        const val MaxBodySize = 512L * 1024 * 1024

    }

}
