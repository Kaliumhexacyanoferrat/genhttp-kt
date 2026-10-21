package org.genhttp.api.content.io

import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseSink

import java.io.InputStream
import java.time.Instant

/**
 * Allows content providers to access a resource without the need to know the actual way of access
 * (such as database, web, or file system).
 *
 * As resources may change (e.g. if a user changes the file that is provided as a resource), content
 * providers must not cache the results of a method call.
 */
public interface Resource {

    /**
     * The name of this resource, if known.
     */
    public val name: String?

    /**
     * The point in time, when the resource was last modified, if known.
     */
    public val modified: Instant?

    /**
     * The content type of this resource, if known.
     */
    public val contentType: ContentType?

    /**
     * The number of bytes provided by this resource.
     *
     * This field will not be used to control the HTTP flow, but just as meta information (e.g. to be rendered
     * by the directory listing handler).
     */
    public val length: Long?

    /**
     * Calculates the checksum of the resource.
     *
     * @return The checksum of the resource
     */
    public suspend fun calculateChecksum(): Long

    /**
     * Returns the read-only stream of the resource to be accessed. The caller is responsible to close it.
     *
     * @return The content of the resource
     */
    public suspend fun getContent(): InputStream

    /**
     * Writes the resource as a response body directly to the given sink.
     *
     * @param sink The sink to write the resource to
     */
    public suspend fun write(sink: ResponseSink)

    /**
     * Anchor for factory functions provided by modules (e.g. `Resource.fromString("...")`).
     */
    public companion object

}
