package org.genhttp.modules.io.streaming

import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseSink

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * Response content backed by an input stream, which will be closed by the engine after the content has been written.
 *
 * As JVM streams cannot be queried for their length, the length of the content is only known if it is passed
 * to the constructor.
 *
 * @param content The stream to be sent to the client
 * @param contentType The type of the content (defaults to [ContentType.ApplicationOctetStream])
 * @param knownLength The length of the content, if known
 * @param encoding The encoding of the content, if any
 * @param checksumProvider Allows to customize the computation of the checksum (the checksum of the stream
 * is computed if it supports resetting, otherwise no checksum is available)
 */
public class StreamContent(
    private val content: InputStream,
    contentType: ContentType?,
    private val knownLength: Long?,
    override val encoding: ByteBuffer?,
    checksumProvider: (suspend () -> Long?)?
) : ResponseContent, AutoCloseable {

    private val checksumProvider = ChecksumProvider(checksumProvider ?: { content.calculateChecksum() })

    private var closed = false

    override val type: ContentType? = contentType ?: ContentType.ApplicationOctetStream

    override val length: Long?
        get() = knownLength

    override suspend fun calculateChecksum(): Long? = checksumProvider.compute()

    /**
     * Writes the content of the stream to the given target.
     *
     * @param target The stream to write the content to
     * @param bufferSize The size of the buffer used to transfer the data
     */
    public suspend fun write(target: OutputStream, bufferSize: Int) {
        content.copyPooled(target, bufferSize)
    }

    override suspend fun write(sink: ResponseSink) {
        write(sink.stream, 8192)
    }

    override fun close() {
        if (!closed) {
            closed = true

            content.close()
        }
    }

}
