package org.genhttp.engine.shared.types.sinks

import org.genhttp.api.protocol.ResponseSink
import org.genhttp.engine.shared.types.ClientContext

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel

/**
 * Sink for content of known length, writing directly to the connection.
 */
internal class RegularSink(private val context: ClientContext) : ResponseSink {

    override val writer: WritableByteChannel
        get() = context.writer

    override val stream: OutputStream
        get() = context.writer.stream

}

/**
 * Sink for content of unknown length, applying chunked transfer encoding.
 */
internal class ChunkedSink(private val context: ClientContext) : ResponseSink {

    private val chunkedWriter = ChunkedWriter(context)

    override val writer: WritableByteChannel
        get() = chunkedWriter

    override val stream: OutputStream = ChunkedStream(chunkedWriter)

    fun finish() = chunkedWriter.finish()

}

/**
 * Writes every block of data as a single chunk.
 */
internal class ChunkedWriter(private val context: ClientContext) : WritableByteChannel {

    override fun write(src: ByteBuffer): Int {
        val count = src.remaining()

        if (count == 0) {
            return 0
        }

        val writer = context.writer

        writer.write(header(count))
        writer.write(src)
        writer.write(CRLF)

        return count
    }

    fun write(bytes: ByteArray, offset: Int, length: Int) {
        if (length > 0) {
            write(ByteBuffer.wrap(bytes, offset, length))
        }
    }

    fun flush() = context.writer.flush()

    fun finish() {
        context.writer.write(TERMINATOR)
    }

    override fun isOpen(): Boolean = true

    override fun close() {
        // owned by the connection
    }

    private companion object {

        val CRLF = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte())

        val TERMINATOR = "0\r\n\r\n".toByteArray(Charsets.US_ASCII)

        private val HEX = "0123456789ABCDEF".toByteArray(Charsets.US_ASCII)

        /**
         * Renders the chunk header with eight hex digits, like GenHTTP does.
         */
        fun header(count: Int): ByteArray {
            val result = ByteArray(10)
            var value = count

            for (pos in 7 downTo 0) {
                result[pos] = HEX[value and 0xF]
                value = value ushr 4
            }

            result[8] = '\r'.code.toByte()
            result[9] = '\n'.code.toByte()

            return result
        }

        private fun org.genhttp.engine.shared.io.ConnectionWriter.write(bytes: ByteArray) = write(bytes, 0, bytes.size)

    }

}

/**
 * Stream writing every block of data as a single chunk.
 */
internal class ChunkedStream(private val writer: ChunkedWriter) : OutputStream() {

    override fun write(b: Int) = writer.write(byteArrayOf(b.toByte()), 0, 1)

    override fun write(b: ByteArray, off: Int, len: Int) = writer.write(b, off, len)

    override fun flush() = writer.flush()

    override fun close() {
        // owned by the connection
    }

}
