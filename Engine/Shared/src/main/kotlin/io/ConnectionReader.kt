package org.genhttp.engine.shared.io

import org.genhttp.api.infrastructure.BufferSize

import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel

/**
 * Buffered, blocking reader on a connection channel (the counterpart of the `PipeReader` used by GenHTTP).
 *
 * Data read from the channel is kept in a contiguous heap buffer. Callers inspect the buffered data via [data],
 * mark processed bytes via [consume] and request more data via [fill]. Views returned by [data] stay valid
 * until the next call to [fill] (which may compact or grow the buffer) and must not be modified.
 *
 * Reads block the calling thread, which is intended to be a virtual thread. Timeouts are enforced by the engine by
 * closing the channel, which aborts a blocked read.
 */
public class ConnectionReader(private val channel: ReadableByteChannel, initialCapacity: Int = BufferSize.Read) {

    private var buffer: ByteArray = ByteArray(initialCapacity)

    // view on the whole buffer, sliced by data(); not read-only, so that the parser can access the backing array
    private var view: ByteBuffer = ByteBuffer.wrap(buffer)

    private var start = 0

    private var end = 0

    /**
     * Whether the end of the stream has been reached.
     */
    public var isCompleted: Boolean = false
        private set

    /**
     * The number of buffered bytes that have not been consumed yet.
     */
    public val available: Int
        get() = end - start

    /**
     * A view on the buffered bytes that have not been consumed yet (position 0, limit [available]).
     *
     * The view is backed by the buffer of the reader (so that parsers can access the array directly) and must not
     * be modified. Data that is handed out to handlers needs to be sliced as read-only buffers.
     */
    public fun data(): ByteBuffer = view.slice(start, end - start)

    /**
     * Marks the given number of buffered bytes as consumed.
     */
    public fun consume(count: Int) {
        require(count in 0..available) { "Cannot consume $count bytes, only $available available" }

        start += count

        if (start == end) {
            start = 0
            end = 0
        }
    }

    /**
     * Reads more data from the channel, blocking until at least one byte is available.
     *
     * @return The number of bytes read, or -1 if the end of the stream has been reached
     */
    public fun fill(): Int {
        if (isCompleted) {
            return -1
        }

        ensureSpace()

        val target = ByteBuffer.wrap(buffer, end, buffer.size - end)

        var read: Int

        do {
            read = channel.read(target)
        } while (read == 0)

        if (read < 0) {
            isCompleted = true
            return -1
        }

        end += read
        return read
    }

    /**
     * Copies up to `length` bytes into the given array, reading from the channel if no data is buffered.
     *
     * @return The number of bytes copied, or -1 if the end of the stream has been reached
     */
    public fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) {
            return 0
        }

        if (available == 0 && fill() < 0) {
            return -1
        }

        val count = minOf(length, available)

        System.arraycopy(buffer, start, target, offset, count)
        consume(count)

        return count
    }

    /**
     * Reads exactly `length` bytes into a new array, combining buffered data with direct channel reads.
     *
     * @throws IOException if the stream ends before the requested number of bytes could be read
     */
    public fun readExactly(length: Int): ByteArray {
        val result = ByteArray(length)

        var position = 0

        while (position < length) {
            val read = read(result, position, length - position)

            if (read < 0) {
                throw IOException("Unexpected end of stream ($position of $length bytes read)")
            }

            position += read
        }

        return result
    }

    /**
     * Exposes the remaining connection content (buffered data first) as an input stream, e.g. after a protocol
     * upgrade. Closing the stream has no effect.
     */
    public fun asInputStream(): InputStream = ReaderStream(this)

    private fun ensureSpace() {
        if (end < buffer.size) {
            return
        }

        val count = end - start

        if (start > 0) {
            // compact: views handed out earlier are invalidated (see class documentation)
            System.arraycopy(buffer, start, buffer, 0, count)
        } else {
            buffer = buffer.copyOf(buffer.size * 2)
            view = ByteBuffer.wrap(buffer)
        }

        start = 0
        end = count
    }

    private class ReaderStream(private val reader: ConnectionReader) : InputStream() {

        override fun read(): Int {
            val single = ByteArray(1)
            val read = read(single, 0, 1)
            return if (read < 0) -1 else single[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int = reader.read(b, off, len)

        override fun available(): Int = reader.available

        override fun close() {
            // owned by the connection
        }

    }

}
