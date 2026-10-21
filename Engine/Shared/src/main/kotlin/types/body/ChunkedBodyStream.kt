package org.genhttp.engine.shared.types.body

import org.genhttp.api.content.ProviderException
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.engine.shared.io.ConnectionReader
import org.genhttp.glyph11.HttpParseException
import org.genhttp.glyph11.parser.ChunkResult

import org.genhttp.glyph11.parser.ChunkedBodyStream as ChunkParser

/**
 * Decodes a chunked request body from the connection.
 */
public class ChunkedBodyStream(private val reader: ConnectionReader) : java.io.InputStream(), DrainableStream {

    private val parser = ChunkParser()

    private var completed = false

    private var overflow: ByteArray? = null

    private var overflowOffset = 0

    private var overflowLength = 0

    override fun read(): Int {
        val single = ByteArray(1)
        return if (read(single, 0, 1) <= 0) -1 else single[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        try {
            if (completed) {
                return -1
            }

            if (len == 0) {
                return 0
            }

            if (overflowLength > 0) {
                return consumeOverflow(b, off, len)
            }

            while (true) {
                if (reader.available == 0 && reader.fill() < 0) {
                    return -1
                }

                val data = reader.data()

                when (parser.tryReadChunk(data)) {
                    ChunkResult.Completed -> {
                        reader.consume(parser.bytesConsumed)
                        completed = true
                        return -1
                    }

                    ChunkResult.NeedMoreData -> {
                        if (reader.fill() < 0) {
                            throw ProviderException(ResponseStatus.BadRequest, "Unexpected end of chunked body")
                        }
                    }

                    ChunkResult.Chunk -> {
                        val chunkLength = parser.dataLength
                        val toCopy = minOf(len, chunkLength)

                        data.get(parser.dataOffset, b, off, toCopy)

                        if (toCopy < chunkLength) {
                            val remaining = ByteArray(chunkLength - toCopy)
                            data.get(parser.dataOffset + toCopy, remaining)

                            overflow = remaining
                            overflowOffset = 0
                            overflowLength = remaining.size
                        }

                        reader.consume(parser.bytesConsumed)

                        if (toCopy == 0) {
                            // empty chunk data cannot happen (zero sized chunks terminate the body), but stay safe
                            continue
                        }

                        return toCopy
                    }
                }
            }
        } catch (e: HttpParseException) {
            throw ProviderException(ResponseStatus.fromCode(e.statusCode) ?: ResponseStatus.BadRequest, "Failed to read chunked encoded body", e)
        }
    }

    override fun drain() {
        overflow = null
        overflowOffset = 0
        overflowLength = 0

        try {
            while (!completed) {
                if (reader.available == 0 && reader.fill() < 0) {
                    return
                }

                when (parser.tryReadChunk(reader.data())) {
                    ChunkResult.Completed -> {
                        reader.consume(parser.bytesConsumed)
                        completed = true
                        return
                    }

                    ChunkResult.NeedMoreData -> {
                        if (reader.fill() < 0) {
                            return
                        }
                    }

                    ChunkResult.Chunk -> reader.consume(parser.bytesConsumed)
                }
            }
        } catch (e: HttpParseException) {
            throw ProviderException(ResponseStatus.fromCode(e.statusCode) ?: ResponseStatus.BadRequest, "Failed to read chunked encoded body", e)
        }
    }

    private fun consumeOverflow(b: ByteArray, off: Int, len: Int): Int {
        val source = overflow ?: return 0
        val toCopy = minOf(len, overflowLength)

        System.arraycopy(source, overflowOffset, b, off, toCopy)

        overflowOffset += toCopy
        overflowLength -= toCopy

        if (overflowLength == 0) {
            overflow = null
            overflowOffset = 0
        }

        return toCopy
    }

    override fun close() {
        // the remaining content is drained by the engine
    }

}
