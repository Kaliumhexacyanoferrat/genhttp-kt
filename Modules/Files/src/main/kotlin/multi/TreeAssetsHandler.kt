package org.genhttp.modules.files.multi

import org.genhttp.api.content.io.CompressionAlgorithm
import org.genhttp.api.content.io.ResourceTree
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.RequestTarget
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.modules.io.find
import org.genhttp.modules.io.streaming.ResourceContent

import java.nio.ByteBuffer

/**
 * A handler that serves the resources of a resource tree.
 *
 * @param tree The tree to be served
 * @param algorithms The algorithms to look for pre-compressed variants of
 * @param separator The separator between the file name and the extension of the compressed variant
 */
public class TreeAssetsHandler(
    private val tree: ResourceTree,
    algorithms: List<CompressionAlgorithm>,
    separator: Char
) : AbstractAssetsHandler(algorithms, separator) {

    override suspend fun resolve(target: RequestTarget, contentType: ContentType?, contentEncoding: ByteBuffer?): ResponseContent? {
        val (_, resource) = tree.find(target)

        if (resource != null) {
            return ResourceContent(resource, contentType, contentEncoding)
        }

        return null
    }

}
