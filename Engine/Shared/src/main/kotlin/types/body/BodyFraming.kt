package org.genhttp.engine.shared.types.body

import java.nio.ByteBuffer

/**
 * Tracks the position within the body of a request as declared by its framing, so that the body can be read or
 * skipped from wherever the consumer left off (`IBodyFraming` in GenHTTP).
 *
 * The framings read from the buffered, blocking reader of the connection: reads only block if the data has not
 * been received yet, which parks the virtual thread of the internal engine. The Netty engine invokes handlers once
 * the body has been received (see the deviation log).
 */
internal interface BodyFraming {

    /**
     * The declared length of the body, if known up front.
     */
    val length: Long?

    /**
     * The number of body bytes handed out so far.
     */
    val position: Long

    /**
     * Copies the next bytes of the body into the given array.
     *
     * @return The number of bytes copied, zero if the body has been read completely (or `length` is zero)
     */
    fun read(target: ByteArray, offset: Int, length: Int): Int

    /**
     * Reads the rest of the body into a single read-only buffer.
     *
     * The buffer may point into the buffer of the connection, so it stays valid only until the body is skipped.
     */
    fun readToEnd(): ByteBuffer

    /**
     * Skips the rest of the body so that the connection is positioned at the start of the next request.
     *
     * @return false, if the body ended early or was malformed, so the connection cannot be reused
     */
    fun skip(): Boolean

    /**
     * Detaches the framing from the connection so it can be reused.
     */
    fun reset()

}
