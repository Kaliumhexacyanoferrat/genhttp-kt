package org.genhttp.engine.shared.types.sinks

import org.genhttp.api.protocol.ResponseSink
import org.genhttp.engine.shared.types.ClientContext

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel

/**
 * Writes every block of data as a single chunk.
 */
internal class ChunkedWriter(private val context: ClientContext) : WritableByteChannel {

    // the header of the current chunk (reused, as the writer belongs to a single connection)
    private val header = ByteArray(10)

    override fun write(src: ByteBuffer): Int {
        val count = src.remaining()

        if (count == 0) {
            return 0
        }

        val writer = context.writer

        writer.write(renderHeader(count))
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

    /**
     * Renders the chunk header with eight hex digits, like GenHTTP does.
     */
    private fun renderHeader(count: Int): ByteArray {
        var value = count

        for (pos in 7 downTo 0) {
            header[pos] = HEX[value and 0xF]
            value = value ushr 4
        }

        header[8] = '\r'.code.toByte()
        header[9] = '\n'.code.toByte()

        return header
    }

    override fun isOpen(): Boolean = true

    override fun close() {
        // owned by the connection
    }

    private companion object {

        val CRLF = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte())

        val TERMINATOR = "0\r\n\r\n".toByteArray(Charsets.US_ASCII)

        private val HEX = "0123456789ABCDEF".toByteArray(Charsets.US_ASCII)

        private fun org.genhttp.engine.shared.io.ConnectionWriter.write(bytes: ByteArray) = write(bytes, 0, bytes.size)

    }

}
