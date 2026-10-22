package org.genhttp.modules.singlepageapplications.provider

import org.genhttp.api.content.Handler
import org.genhttp.api.content.io.ResourceTree
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.PathSegment
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.modules.files.Assets
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.denyPathTraversal

/**
 * A handler that serves the files of a single page application and falls back
 * to its index file for unknown routes, if server side routing is enabled.
 *
 * @param tree The tree to serve the application from
 * @param serverSideRouting Whether the index should be served for every unknown route
 */
public class SinglePageProvider(private val tree: ResourceTree, private val serverSideRouting: Boolean) : Handler {

    @Volatile
    private var index: Handler? = null

    private val resources: Handler = Assets.from(tree).build()

    // region Functionality

    override suspend fun handle(request: Request): Response? {
        val target = request.header.target

        if (target.current == null) {
            val index = getIndex()

            if (index != null) {
                return index.handle(request)
            }
        } else {
            target.denyPathTraversal()

            val result = resources.handle(request)

            if (result == null) {
                val index = getIndex()

                if (index != null && serverSideRouting) {
                    return index.handle(request)
                }
            }

            return result
        }

        return null
    }

    private suspend fun getIndex(): Handler? {
        if (index == null) {
            for (name in IndexFiles) {
                val indexFile = tree.tryGetResource(name)

                if (indexFile != null) {
                    index = Content.from(indexFile)
                        .build()

                    break
                }
            }
        }

        return index
    }

    override suspend fun prepare(server: Server) {
        resources.prepare(server)
    }

    // endregion

    private companion object {

        val IndexFiles: Set<PathSegment> = linkedSetOf(PathSegment("index.html"), PathSegment("index.htm"))

    }

}
