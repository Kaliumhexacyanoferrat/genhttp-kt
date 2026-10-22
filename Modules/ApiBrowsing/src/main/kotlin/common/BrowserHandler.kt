package org.genhttp.modules.apibrowsing.common

import org.genhttp.api.content.Handler
import org.genhttp.api.content.ProviderException
import org.genhttp.api.content.io.Resource
import org.genhttp.api.content.io.ResourceTree
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.PathSegment
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.apibrowsing.ApiBrowser
import org.genhttp.modules.files.Assets
import org.genhttp.modules.io.fromClasspath
import org.genhttp.modules.io.hasType
import org.genhttp.modules.pages.Renderer
import org.genhttp.modules.pages.getPage
import org.genhttp.modules.pages.rendering.TemplateRenderer

/**
 * A handler that serves a graphical API browser (the rendered index page and its static resources).
 *
 * @param resourceRoot The name of the folder providing the template and static files of the application
 * @param metaData The configuration of the application
 */
public class BrowserHandler(resourceRoot: String, metaData: BrowserMetaData) : Handler {

    // region Get-/Setters

    /**
     * The handler serving the static files (scripts, stylesheets) of the application.
     */
    public val staticResources: Handler = Assets.from(ResourceTree.fromClasspath(ApiBrowser::class, "$resourceRoot/Static"))
        .build()

    /**
     * The renderer of the index page.
     */
    public val template: TemplateRenderer = Renderer.from(Resource.fromClasspath(ApiBrowser::class, "$resourceRoot/Index.html").build())

    /**
     * The configuration of the application.
     */
    public val metaData: BrowserMetaData = metaData

    // endregion

    // region Functionality

    override suspend fun prepare(server: Server) {
        staticResources.prepare(server)
    }

    override suspend fun handle(request: Request): Response? {
        if (!request.hasType(RequestMethod.Get, RequestMethod.Head)) {
            throw ProviderException(ResponseStatus.MethodNotAllowed, "Only GET requests are allowed by this handler") {
                header("Allow", "GET")
            }
        }

        val target = request.header.target

        val current = target.current

        if (current == null) {
            val config = mapOf(
                "title" to metaData.title,
                "url" to (metaData.url ?: "../openapi.json")
            )

            val content = template.render(config)

            return request.getPage(content)
                .build()
        }

        if (current == StaticSegment) {
            target.advance()
            return staticResources.handle(request)
        }

        return null
    }

    // endregion

    private companion object {

        val StaticSegment = PathSegment("static")

    }

}
