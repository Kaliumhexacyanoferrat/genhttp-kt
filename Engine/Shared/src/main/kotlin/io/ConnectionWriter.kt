package org.genhttp.engine.shared.io

import org.genhttp.api.infrastructure.BufferSize
import org.genhttp.api.infrastructure.ConnectionClosedException

import java.io.Flushable
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel

/**
 * Buffered, blocking writer on a connection channel (the counterpart of the `PipeWriter` used by GenHTTP).
 *
 * Data is collected in a direct buffer and sent to the channel when the buffer is full or when [flush] is called.
 * Failures to write to the underlying channel (the client went away) are reported as [ConnectionClosedException].
 *
 * Closing the writer has no effect, the connection is owned by the engine.
 */
public class ConnectionWriter(private val channel: WritableByteChannel, capacity: Int = BufferSize.Write) : WritableByteChannel, Flushable {

    private val buffer: ByteBuffer = ByteBuffer.allocateDirect(capacity)

    /**
     * An output stream writing to this writer. Flushing the stream sends the buffered data to the client.
     */
    public val stream: OutputStream = WriterStream(this)

    /**
     * The number of bytes that have been buffered but not sent yet.
     */
    public val pending: Int
        get() = buffer.position()

    override fun write(src: ByteBuffer): Int {
        val count = src.remaining()

        if (count > buffer.remaining()) {
            flush()

            if (count >= buffer.capacity()) {
                send(src)
                return count
            }
        }

        buffer.put(src)
        return count
    }

    /**
     * Writes the given bytes.
     */
    public fun write(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) {
        if (length <= buffer.remaining()) {
            buffer.put(bytes, offset, length)
        } else {
            write(ByteBuffer.wrap(bytes, offset, length))
        }
    }

    /**
     * Writes a single byte.
     */
    public fun writeByte(value: Int) {
        if (!buffer.hasRemaining()) {
            flush()
        }

        buffer.put(value.toByte())
    }

    /**
     * Writes the decimal representation of the given number.
     */
    public fun writeNumber(value: Long) {
        if (value < 0) {
            writeAscii(value.toString())
            return
        }

        var digits = 1
        var limit = 10L

        while (digits < 19 && value >= limit) {
            digits++
            limit *= 10
        }

        if (digits > buffer.remaining()) {
            flush()
        }

        var remaining = value
        val start = buffer.position()

        for (i in digits - 1 downTo 0) {
            buffer.put(start + i, ('0'.code + (remaining % 10).toInt()).toByte())
            remaining /= 10
        }

        buffer.position(start + digits)
    }

    /**
     * Writes the given ASCII string.
     */
    public fun writeAscii(value: String) {
        if (value.length > buffer.remaining()) {
            flush()
        }

        if (value.length > buffer.remaining()) {
            write(value.toByteArray(Charsets.ISO_8859_1))
            return
        }

        for (i in value.indices) {
            buffer.put(value[i].code.toByte())
        }
    }

    /**
     * Sends all buffered data to the client.
     *
     * @throws ConnectionClosedException if the client closed the connection
     */
    override fun flush() {
        if (buffer.position() == 0) {
            return
        }

        buffer.flip()

        try {
            send(buffer)
        } finally {
            buffer.clear()
        }
    }

    override fun isOpen(): Boolean = channel.isOpen

    override fun close() {
        // owned by the connection
    }

    private fun send(data: ByteBuffer) {
        try {
            while (data.hasRemaining()) {
                channel.write(data)
            }
        } catch (e: ConnectionClosedException) {
            throw e
        } catch (e: IOException) {
            throw ConnectionClosedException().apply { initCause(e) }
        }
    }

    private class WriterStream(private val writer: ConnectionWriter) : OutputStream() {

        override fun write(b: Int) = writer.writeByte(b)

        override fun write(b: ByteArray, off: Int, len: Int) = writer.write(b, off, len)

        override fun flush() = writer.flush()

        override fun close() {
            // owned by the connection
        }

    }

}
