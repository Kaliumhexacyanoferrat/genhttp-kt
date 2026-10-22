package org.genhttp.modules.directorybrowsing.provider

import org.genhttp.api.content.Handler
import org.genhttp.api.content.io.ResourceTree
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.denyPathTraversal
import org.genhttp.modules.io.find

/**
 * A handler that serves the resources of a tree and renders listings for its directories.
 *
 * @param tree The tree to be served
 */
public class ListingRouter(private val tree: ResourceTree) : Handler {

    // region Functionality

    override suspend fun handle(request: Request): Response? {
        val target = request.header.target

        target.denyPathTraversal()

        val (node, resource) = tree.find(target)

        if (resource != null) {
            return Content.from(resource)
                .build()
                .handle(request)
        }

        if (node != null) {
            return ListingProvider(node).handle(request)
        }

        return null
    }

    override suspend fun prepare(server: Server) {
    }

    // endregion

}
