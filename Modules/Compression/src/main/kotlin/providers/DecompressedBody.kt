package org.genhttp.modules.compression.providers

import org.genhttp.api.content.io.CompressionAlgorithm
import org.genhttp.api.protocol.RequestBody

import java.io.InputStream
import java.nio.ByteBuffer

/**
 * A request body that decompresses the content of the wrapped body on read.
 *
 * Closing the body closes the decompressing stream (the wrapped body stays untouched).
 *
 * @param body The body sent by the client
 * @param algorithm The algorithm used to decompress the body
 */
public class DecompressedBody(body: RequestBody, algorithm: CompressionAlgorithm) : RequestBody, AutoCloseable {

    private val decompressedStream: InputStream = algorithm.decompress(body.asStream())

    override fun asStream(): InputStream = decompressedStream

    override suspend fun asMemory(): ByteBuffer = ByteBuffer.wrap(decompressedStream.readAllBytes()).asReadOnlyBuffer()

    override fun close() {
        decompressedStream.close()
    }

}
