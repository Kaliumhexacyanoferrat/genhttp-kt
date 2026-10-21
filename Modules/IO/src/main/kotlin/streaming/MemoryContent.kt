package org.genhttp.modules.io.streaming

import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseSink

import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * Response content backed by a buffer of bytes.
 *
 * @param content The bytes to be sent to the client (from the position to the limit of the buffer, which is not modified)
 * @param contentType The type of the content (defaults to [ContentType.ApplicationOctetStream])
 * @param checksumProvider Allows to customize the computation of the checksum
 */
public class MemoryContent(
    content: ByteBuffer,
    contentType: ContentType?,
    checksumProvider: (suspend () -> Long?)?
) : ResponseContent {

    private val content: ByteBuffer = content.duplicate().asReadOnlyBuffer()

    private val checksumProvider = ChecksumProvider(checksumProvider ?: { this.content.computeChecksum() })

    override val length: Long? = this.content.remaining().toLong()

    override val type: ContentType? = contentType ?: ContentType.ApplicationOctetStream

    override val encoding: ByteBuffer? = null

    override suspend fun calculateChecksum(): Long? = checksumProvider.compute()

    /**
     * Writes the content to the given stream.
     *
     * @param target The stream to write the content to
     * @param bufferSize The size of the buffer used to transfer the data (not used by this implementation)
     */
    @Suppress("UNUSED_PARAMETER")
    public suspend fun write(target: OutputStream, bufferSize: Int) {
        val view = content.duplicate()

        if (view.hasArray()) {
            target.write(view.array(), view.arrayOffset() + view.position(), view.remaining())
        } else {
            val bytes = ByteArray(view.remaining())

            view.get(bytes)

            target.write(bytes)
        }
    }

    override suspend fun write(sink: ResponseSink) {
        sink.writer.writeFully(content.duplicate())
    }

}
