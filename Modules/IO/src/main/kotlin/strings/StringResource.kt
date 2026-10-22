package org.genhttp.modules.io.strings

import org.genhttp.api.content.io.Resource
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseSink
import org.genhttp.modules.io.streaming.writeFully

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.time.Instant

/**
 * A resource backed by a string, which is provided UTF-8 encoded.
 *
 * @param content The content of the resource
 * @param name The name of the resource, if any
 * @param contentType The type of the resource (defaults to [ContentType.TextPlain])
 * @param modified The modification date of the resource, if known
 */
public class StringResource(content: String, override val name: String?, contentType: ContentType?, override val modified: Instant?) : Resource {

    /**
     * The UTF-8 encoded content of the resource.
     */
    public val content: ByteArray = content.toByteArray(Charsets.UTF_8)

    private val checksum: Long = content.hashCode().toLong()

    override val contentType: ContentType? = contentType ?: ContentType.TextPlain

    override val length: Long? = this.content.size.toLong()

    override suspend fun getContent(): InputStream = ByteArrayInputStream(content)

    override suspend fun calculateChecksum(): Long = checksum

    /**
     * Writes the content of the resource to the given stream.
     *
     * @param target The stream to write the content to
     * @param bufferSize The size of the buffer used to transfer the data (not used by this implementation)
     */
    @Suppress("UNUSED_PARAMETER")
    public suspend fun write(target: OutputStream, bufferSize: Int) {
        target.write(content)
    }

    override suspend fun write(sink: ResponseSink) {
        sink.writer.writeFully(ByteBuffer.wrap(content))
    }

}
