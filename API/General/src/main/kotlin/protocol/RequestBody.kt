package org.genhttp.api.protocol

import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Grants access to the body of the HTTP request.
 */
public interface RequestBody {

    /**
     * Reads the content of the body as a stream.
     *
     * @return The stream representing the body of the request
     */
    public fun asStream(): InputStream

    /**
     * Reads the content of the body as a memory view.
     *
     * @return The read-only buffer representing the body of the request (from position to limit)
     */
    public suspend fun asMemory(): ByteBuffer

}
