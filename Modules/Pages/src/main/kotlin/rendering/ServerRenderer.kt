package org.genhttp.modules.pages.rendering

import org.genhttp.modules.io.fromClasspath
import org.genhttp.modules.io.track
import org.genhttp.modules.pages.Renderer
import org.genhttp.modules.pages.escaped

import org.genhttp.api.content.io.Resource

/**
 * Renders HTML pages in the style of the server (a title and some content).
 */
public class ServerRenderer {

    // not created via Renderer.from to prevent a cycle during the initialization of Renderer
    private val templateRender = TemplateRenderer(
        Resource.fromClasspath(Renderer::class, "ServerPage.html").build().track()
    )

    /**
     * Renders a server-styled HTML page for the given title and content.
     *
     * The title will be HTML-escaped by this method, the content will not.
     *
     * @param title The title of the page to be rendered (plain text)
     * @param content The HTML content of the page
     * @return The generated HTML page
     */
    public suspend fun render(title: String, content: String): String =
        templateRender.render(mapOf("title" to title.escaped(), "content" to content))

    /**
     * Renders a server-styled HTML error page for the given exception.
     *
     * @param title The title of the page to be rendered
     * @param error The error which has ocurred
     * @param developmentMode Whether additional error information should be printed
     * @return The generated HTML error page
     */
    public suspend fun render(title: String, error: Throwable, developmentMode: Boolean): String =
        render(title, if (developmentMode) error.stackTraceToString().escaped() else (error.message ?: error.javaClass.name).escaped())

}
