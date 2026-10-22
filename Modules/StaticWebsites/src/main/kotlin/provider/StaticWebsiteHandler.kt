package org.genhttp.modules.staticwebsites.provider

import org.genhttp.api.content.Handler
import org.genhttp.api.content.io.ResourceTree
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.PathSegment
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.files.Assets
import org.genhttp.modules.io.denyPathTraversal
import org.genhttp.modules.io.find
import org.genhttp.modules.io.streaming.ResourceContent

/**
 * A handler that serves the files of a static website, including the index files of directories.
 *
 * @param tree The tree to serve the website from
 */
public class StaticWebsiteHandler(private val tree: ResourceTree) : Handler {

    private val resources: Handler = Assets.from(tree)
        .build()

    // region Functionality

    override suspend fun handle(request: Request): Response? {
        val target = request.header.target

        target.denyPathTraversal()

        if (target.hasTrailingSlash) {
            val (node, _) = tree.find(target)

            if (node != null) {
                for (indexFile in IndexFiles) {
                    val file = node.tryGetResource(indexFile)

                    if (file != null) {
                        return request.respond()
                            .status(ResponseStatus.Ok)
                            .content(ResourceContent(file))
                            .build()
                    }
                }
            }

            return null
        }

        return resources.handle(request)
    }

    override suspend fun prepare(server: Server) {
        resources.prepare(server)
    }

    // endregion

    private companion object {

        val IndexFiles: List<PathSegment> = listOf(PathSegment("index.html"), PathSegment("index.htm"))

    }

}
