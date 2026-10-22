package org.genhttp.modules.files.multi

import org.genhttp.api.content.Handler
import org.genhttp.api.content.io.CompressionAlgorithm
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.PathSegment
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.RequestTarget
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.modules.io.denyPathTraversal
import org.genhttp.modules.io.guessContentType

import java.nio.ByteBuffer

/**
 * Base class of handlers serving a collection of assets, optionally using pre-compressed variants of them.
 *
 * @param algorithms The algorithms to look for pre-compressed variants of
 * @param separator The separator between the file name and the extension of the compressed variant
 */
public abstract class AbstractAssetsHandler protected constructor(
    algorithms: List<CompressionAlgorithm>,
    separator: Char
) : Handler {

    private val preCompression = PreCompression(algorithms, separator)

    override suspend fun prepare(server: Server) {
    }

    override suspend fun handle(request: Request): Response? {
        val target = request.header.target

        if (target.hasTrailingSlash) {
            return null
        }

        target.denyPathTraversal()

        if (preCompression.enabled) {
            val handled = tryGetPreCompressed(request)

            if (handled != null) {
                return handled
            }
        }

        val content = resolve(target)

        if (content != null) {
            var response = request.respond().content(content)

            // The identity response still varies by Accept-Encoding whenever a compressed variant
            // could have been served to a different client.
            if (preCompression.enabled) {
                response = response.header("Vary", "Accept-Encoding")
            }

            return response.build()
        }

        return null
    }

    private suspend fun tryGetPreCompressed(request: Request): Response? {
        val target = request.header.target

        for (supported in preCompression.accepted(request)) {
            val newTarget = target.copyAndAppend(supported.extension)

            val fileName = getFileName(target)

            val contentType = fileName?.guessContentType() ?: ContentType.ApplicationOctetStream

            val content = resolve(newTarget, contentType, supported.algorithm.name.bytes)

            if (content != null) {
                return request.respond()
                    .content(content)
                    .header("Vary", "Accept-Encoding")
                    .build()
            }
        }

        return null
    }

    /**
     * Resolves the content addressed by the given target.
     *
     * @param target The target to be resolved
     * @param contentType The content type to be used instead of the guessed one
     * @param contentEncoding The encoding of the resolved content (for pre-compressed variants)
     * @return The content of the asset, or null if there is no such asset
     */
    protected abstract suspend fun resolve(
        target: RequestTarget,
        contentType: ContentType? = null,
        contentEncoding: ByteBuffer? = null
    ): ResponseContent?

    private fun getFileName(target: RequestTarget): String? {
        if (target.hasTrailingSlash) {
            return null
        }

        var offset = 0

        var current: PathSegment? = null

        while (true) {
            val next = target.next(offset++) ?: break

            current = next
        }

        return current?.decode()
    }

}
