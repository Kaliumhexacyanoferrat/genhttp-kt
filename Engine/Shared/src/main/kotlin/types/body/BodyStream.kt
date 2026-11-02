package org.genhttp.engine.shared.types.body

import java.io.InputStream

/**
 * Provides the body of a request as a read-only stream.
 *
 * Streams on the JVM cannot be written to, seeked or flushed, so only reading is offered. The length and the
 * position of the stream (`Stream.Length` and `Stream.Position` upstream) are available via [length] and [position].
 */
internal class BodyStream(private val framing: BodyFraming) : InputStream() {

    // region Get-/Setters

    /**
     * The declared length of the body.
     *
     * @throws UnsupportedOperationException if the length of the body is not known (chunked bodies)
     */
    val length: Long
        get() = framing.length ?: throw UnsupportedOperationException("Length is not known for chunked streams")

    /**
     * The number of bytes read from the stream so far.
     */
    val position: Long
        get() = framing.position

    // endregion

    // region Functionality

    override fun read(): Int {
        val single = ByteArray(1)
        return if (read(single, 0, 1) <= 0) -1 else single[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) {
            return 0
        }

        val read = framing.read(b, off, len)

        return if (read == 0) -1 else read
    }

    override fun close() {
        // the remaining content is skipped by the engine
    }

    // endregion

}
