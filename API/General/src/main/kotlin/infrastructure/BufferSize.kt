package org.genhttp.api.infrastructure

/**
 * Definition of the buffer sizes used by the engines.
 *
 * Use these buffer sizes in performance critical, custom handler implementations to match the buffer sizes used by
 * the engines. This avoids unnecessary copying and resizing of buffers.
 */
public object BufferSize {

    /**
     * The buffer size used to read incoming requests from the underlying connection.
     *
     * The read buffer size is chosen to ensure that almost any request can be read in one continuous block from
     * the OS buffer.
     */
    public const val Read: Int = 16 * 1024

    /**
     * The buffer size used to write response data to the underlying connection.
     *
     * The write buffer size aligns with the typical OS socket buffer size to avoid too many write operations while
     * also avoiding the write buffer from being split when sending data.
     */
    public const val Write: Int = 64 * 1024

}
