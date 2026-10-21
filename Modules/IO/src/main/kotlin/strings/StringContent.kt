package org.genhttp.modules.io.strings

import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseSink
import org.genhttp.modules.io.streaming.writeFully

import java.nio.ByteBuffer

/**
 * Response content backed by a string, which is sent UTF-8 encoded.
 *
 * @param content The text to be sent to the client
 * @param contentType The type of the content (defaults to [ContentType.TextPlain])
 */
public class StringContent(content: String, contentType: ContentType? = null) : ResponseContent {

    private val bytes: ByteArray = content.toByteArray(Charsets.UTF_8)

    private val checksum: Long = content.hashCode().toLong()

    override val length: Long? = bytes.size.toLong()

    override val type: ContentType? = contentType ?: ContentType.TextPlain

    override val encoding: ByteBuffer? = null

    override suspend fun calculateChecksum(): Long? = checksum

    override suspend fun write(sink: ResponseSink) {
        sink.writer.writeFully(ByteBuffer.wrap(bytes))
    }

}
