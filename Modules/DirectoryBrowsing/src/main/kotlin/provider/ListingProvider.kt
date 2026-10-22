package org.genhttp.modules.directorybrowsing.provider

import org.genhttp.api.content.Handler
import org.genhttp.api.content.io.ResourceContainer
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.modules.pages.Renderer
import org.genhttp.modules.pages.getPage

/**
 * A handler that renders a listing of the content of a resource container.
 *
 * @param container The container to be listed
 */
public class ListingProvider(public val container: ResourceContainer) : Handler {

    // region Functionality

    override suspend fun handle(request: Request): Response? {
        val target = request.header.target

        val model = ListingModel(container, target.isLast)

        val content = ListingRenderer.render(model)

        val page = Renderer.server.render("Index of ${target.asString()}", content)

        return request.getPage(page).build()
    }

    override suspend fun prepare(server: Server) {
    }

    // endregion

}
