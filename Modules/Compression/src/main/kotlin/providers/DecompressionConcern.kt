package org.genhttp.modules.compression.providers

import org.genhttp.api.content.Handler
import org.genhttp.api.content.io.AlgorithmName
import org.genhttp.api.content.io.CompressionAlgorithm
import org.genhttp.api.content.services.ApiRequestHeader
import org.genhttp.api.content.services.DocumentedConcern
import org.genhttp.api.content.services.OperationDocumentation
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.KnownHeaders
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.getEntry

/**
 * Concern that automatically decompresses incoming request content
 * based on the Content-Encoding header.
 *
 * @param content The handler to be wrapped by this concern
 * @param algorithms The algorithms that can be used to decompress request bodies, by their name
 */
public class DecompressionConcern(
    override val content: Handler,
    private val algorithms: Map<AlgorithmName, CompressionAlgorithm>
) : DocumentedConcern {

    override suspend fun handle(request: Request): Response? {
        val encoding = request.header.headers.getEntry(KnownHeaders.ContentEncoding)

        if (encoding != null) {
            val requestedAlgorithm = AlgorithmName(encoding.bytes)

            val algorithm = algorithms[requestedAlgorithm]

            if (algorithm != null) {
                request.wrapBody { DecompressedBody(it, algorithm) }
            }
        }

        return content.handle(request)
    }

    override suspend fun prepare(server: Server) {
        content.prepare(server)
    }

    override fun addDocumentation(operation: OperationDocumentation) {
        if (operation.method != RequestMethod.Get && operation.method != RequestMethod.Head) {
            val supported = algorithms.keys.joinToString(", ") { it.toString() }

            operation.add(ApiRequestHeader(name = "Content-Encoding", description = "Allows to send a compressed request body (supported: $supported)"))
        }
    }

}
