package org.genhttp.modules.io.streaming

import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseSink

import java.nio.ByteBuffer

/**
 * Response content backed by a byte array.
 *
 * @param content The bytes to be sent to the client
 * @param contentType The type of the content (defaults to [ContentType.ApplicationOctetStream])
 * @param checksumProvider Allows to customize the computation of the checksum
 */
public class ByteArrayContent(
    private val content: ByteArray,
    contentType: ContentType?,
    checksumProvider: (suspend () -> Long?)?
) : ResponseContent {

    private val checksumProvider = ChecksumProvider(checksumProvider ?: { content.computeChecksum() })

    override val length: Long? = content.size.toLong()

    override val type: ContentType? = contentType ?: ContentType.ApplicationOctetStream

    override val encoding: ByteBuffer? = null

    override suspend fun calculateChecksum(): Long? = checksumProvider.compute()

    override suspend fun write(sink: ResponseSink) {
        sink.writer.writeFully(ByteBuffer.wrap(content))
    }

}
