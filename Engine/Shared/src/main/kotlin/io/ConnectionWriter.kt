package org.genhttp.engine.shared.io

import org.genhttp.api.MemoryView
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
 * The buffer is rented from a pool as soon as data is written and returned when it has been flushed.
 * Failures to write to the underlying channel (the client went away) are reported as [ConnectionClosedException].
 *
 * Closing the writer has no effect, the connection is owned by the engine.
 */
public class ConnectionWriter(private val channel: WritableByteChannel, private val capacity: Int = BufferSize.Write) : WritableByteChannel, Flushable {

    // only rented while there is data to be sent
    private var current: ByteBuffer? = null

    /**
     * An output stream writing to this writer. Flushing the stream sends the buffered data to the client.
     */
    public val stream: OutputStream = WriterStream(this)

    /**
     * The number of bytes that have been buffered but not sent yet.
     */
    public val pending: Int
        get() = current?.position() ?: 0

    override fun write(src: ByteBuffer): Int {
        val count = src.remaining()

        if (count > capacity - pending) {
            flush()

            if (count >= capacity) {
                send(src)
                return count
            }
        }

        buffer().put(src)
        return count
    }

    /**
     * Writes the content (position until limit) of the given buffer without changing the buffer, so that buffers
     * shared with others do not need to be duplicated first.
     */
    public fun copyFrom(src: ByteBuffer) {
        val count = src.remaining()

        if (count > capacity - pending) {
            flush()

            if (count >= capacity) {
                send(src.duplicate())
                return
            }
        }

        val buffer = buffer()
        val position = buffer.position()

        buffer.put(position, src, src.position(), count)
        buffer.position(position + count)
    }

    /**
     * Writes the bytes of the given view.
     */
    public fun write(view: MemoryView) {
        val count = view.length

        if (count > capacity - pending) {
            flush()

            if (count >= capacity) {
                send(view.bytes)
                return
            }
        }

        view.copyTo(buffer())
    }

    /**
     * Writes the given bytes.
     */
    public fun write(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size) {
        if (length <= capacity - pending) {
            buffer().put(bytes, offset, length)
        } else {
            write(ByteBuffer.wrap(bytes, offset, length))
        }
    }

    /**
     * Writes a single byte.
     */
    public fun writeByte(value: Int) {
        if (pending == capacity) {
            flush()
        }

        buffer().put(value.toByte())
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

        if (digits > capacity - pending) {
            flush()
        }

        val buffer = buffer()

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
        if (value.length > capacity - pending) {
            flush()
        }

        if (value.length > capacity) {
            write(value.toByteArray(Charsets.ISO_8859_1))
            return
        }

        val buffer = buffer()

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
        val buffer = current ?: return

        try {
            if (buffer.position() > 0) {
                buffer.flip()
                send(buffer)
            }
        } finally {
            current = null
            WriteBuffers.release(buffer)
        }
    }

    override fun isOpen(): Boolean = channel.isOpen

    override fun close() {
        // owned by the connection
    }

    private fun buffer(): ByteBuffer = current ?: WriteBuffers.rent(capacity).also { current = it }

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
