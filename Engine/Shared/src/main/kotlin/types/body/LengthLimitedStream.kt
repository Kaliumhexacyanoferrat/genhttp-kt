package org.genhttp.engine.shared.types.body

import org.genhttp.engine.shared.io.ConnectionReader

import java.io.InputStream

/**
 * Reads a body of known length from the connection.
 */
public class LengthLimitedStream(private val reader: ConnectionReader, private val contentLength: Long) : InputStream(), DrainableStream {

    private var bytesRemaining = contentLength

    /**
     * The number of bytes read so far.
     */
    public val position: Long
        get() = contentLength - bytesRemaining

    override fun read(): Int {
        val single = ByteArray(1)
        return if (read(single, 0, 1) <= 0) -1 else single[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (bytesRemaining <= 0) {
            return -1
        }

        if (len == 0) {
            return 0
        }

        val toRead = minOf(len.toLong(), bytesRemaining).toInt()
        val read = reader.read(b, off, toRead)

        if (read < 0) {
            return -1
        }

        bytesRemaining -= read
        return read
    }

    override fun available(): Int = minOf(reader.available.toLong(), bytesRemaining).toInt()

    override fun drain() {
        while (bytesRemaining > 0) {
            if (reader.available == 0 && reader.fill() < 0) {
                break
            }

            val toSkip = minOf(reader.available.toLong(), bytesRemaining).toInt()

            reader.consume(toSkip)
            bytesRemaining -= toSkip
        }
    }

    override fun close() {
        // the remaining content is drained by the engine
    }

}
