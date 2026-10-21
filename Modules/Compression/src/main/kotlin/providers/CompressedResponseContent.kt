package org.genhttp.modules.compression.providers

import org.genhttp.api.content.io.AlgorithmName
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseSink

import java.nio.ByteBuffer

/**
 * Response content that compresses the original content while it is written to the client.
 *
 * As the compressed length is not known upfront, the content is sent using chunked transfer encoding.
 *
 * @param originalContent The content to be compressed
 * @param sinkFactory Creates the sink compressing the data written by the original content (if the sink
 * implements [AutoCloseable], it is closed after the content has been written to finish the compression)
 * @param algorithmName The name of the algorithm that is used to compress the content
 */
public class CompressedResponseContent(
    private val originalContent: ResponseContent,
    private val sinkFactory: (ResponseSink) -> ResponseSink,
    algorithmName: AlgorithmName
) : ResponseContent, AutoCloseable {

    private var closed = false

    override val length: Long?
        get() = null

    override val type: ContentType?
        get() = originalContent.type

    override val encoding: ByteBuffer? = algorithmName.bytes

    override suspend fun calculateChecksum(): Long? = originalContent.calculateChecksum()

    override suspend fun write(sink: ResponseSink) {
        val compressingSink = sinkFactory(sink)

        try {
            originalContent.write(compressingSink)
        } finally {
            if (compressingSink is AutoCloseable) {
                compressingSink.close()
            }
        }
    }

    override fun close() {
        if (!closed) {
            closed = true

            if (originalContent is AutoCloseable) {
                originalContent.close()
            }
        }
    }

}
