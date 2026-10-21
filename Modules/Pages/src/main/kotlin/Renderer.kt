package org.genhttp.modules.pages

import org.genhttp.api.content.io.Resource
import org.genhttp.modules.io.track
import org.genhttp.modules.pages.rendering.ServerRenderer
import org.genhttp.modules.pages.rendering.TemplateRenderer

/**
 * Thin wrappers to render HTML pages using the Pebble template engine.
 *
 * To serve a page, you might want to use [getPage].
 */
public object Renderer {

    /**
     * A renderer which can be used to generate a HTML page in the
     * server theme passing just title and content.
     */
    public val server: ServerRenderer = ServerRenderer()

    /**
     * Creates a new renderer for the given template file.
     *
     * @param template The template to be used for rendering
     * @return The newly created renderer
     */
    public fun from(template: Resource): TemplateRenderer = TemplateRenderer(template.track())

}
