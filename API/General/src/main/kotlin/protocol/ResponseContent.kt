package org.genhttp.api.protocol

import java.nio.ByteBuffer

/**
 * A specific piece of content that can be sent to a connected client in the HTTP response body.
 */
public interface ResponseContent {

    /**
     * The length of the response body.
     *
     * If this property returns null, the server will apply chunked transfer encoding. Therefore, try to provide
     * the exact length of the response, if known. The length needs to match the actual number of bytes being
     * written in [write] or the client connection will fail.
     */
    public val length: Long?

    /**
     * The type of content sent to the client.
     */
    public val type: ContentType?

    /**
     * The encoding of the content, if any (such as "br").
     */
    public val encoding: ByteBuffer?

    /**
     * Calculates a checksum for the content.
     *
     * Used by several handlers and concerns to check, whether the content has changed. Implementation should be
     * efficient as possible while still noticing changes.
     *
     * @return The checksum of the content
     */
    public suspend fun calculateChecksum(): Long?

    /**
     * Writes the content to the given sink.
     *
     * @param sink The sink to write the content to
     */
    public suspend fun write(sink: ResponseSink)

}
