package org.genhttp.modules.io.streaming

import org.genhttp.api.content.io.Resource
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseSink
import org.genhttp.modules.io.guessContentType

import java.nio.ByteBuffer

/**
 * Response content that is generated from a resource.
 *
 * @param resource The resource to be sent to the client
 * @param contentType The type of the content (guessed from the resource if not specified)
 * @param encoding The encoding of the content, if any
 */
public class ResourceContent(
    private val resource: Resource,
    contentType: ContentType? = null,
    override val encoding: ByteBuffer? = null
) : ResponseContent {

    override val length: Long?
        get() = resource.length

    override val type: ContentType? = contentType ?: resource.guessContentType()

    override suspend fun calculateChecksum(): Long? = resource.calculateChecksum()

    override suspend fun write(sink: ResponseSink) {
        resource.write(sink)
    }

}
