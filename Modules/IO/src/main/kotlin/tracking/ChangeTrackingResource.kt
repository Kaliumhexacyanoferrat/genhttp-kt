package org.genhttp.modules.io.tracking

import org.genhttp.api.content.io.Resource
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseSink

import java.io.InputStream
import java.time.Instant

/**
 * Wraps a resource and keeps track of the changes of its content.
 *
 * @param source The resource to be tracked
 */
public class ChangeTrackingResource(private val source: Resource) : Resource {

    private var lastChecksum: Long? = null

    override val name: String?
        get() = source.name

    override val modified: Instant?
        get() = source.modified

    override val contentType: ContentType?
        get() = source.contentType

    override val length: Long?
        get() = source.length

    override suspend fun getContent(): InputStream {
        lastChecksum = calculateChecksum()

        return source.getContent()
    }

    override suspend fun write(sink: ResponseSink) {
        lastChecksum = calculateChecksum()

        source.write(sink)
    }

    override suspend fun calculateChecksum(): Long = source.calculateChecksum()

    /**
     * Checks whether the content of the resource has changed
     * since [getContent] or [write] has been called the last time.
     *
     * @return True if the content has changed, false otherwise
     */
    public suspend fun checkChanged(): Boolean = calculateChecksum() != lastChecksum

}
