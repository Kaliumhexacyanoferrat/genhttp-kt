package org.genhttp.modules.pages.rendering

import io.pebbletemplates.pebble.PebbleEngine
import io.pebbletemplates.pebble.loader.StringLoader
import io.pebbletemplates.pebble.template.PebbleTemplate

import org.genhttp.modules.io.tracking.ChangeTrackingResource

import java.io.StringWriter

/**
 * Renders a template (written in Pebble syntax) that is read from a resource. The template is
 * compiled once and compiled again if the content of the resource changes.
 *
 * @param template The resource providing the template
 */
public class TemplateRenderer(public val template: ChangeTrackingResource) {

    @Volatile
    private var document: PebbleTemplate? = null

    /**
     * Renders the template with the given model.
     *
     * @param model The model to be used for rendering
     * @return The generated response
     */
    public suspend fun render(model: Map<String, Any?>): String {
        var current = document

        if (current == null || template.checkChanged()) {
            val source = template.getContent().use { it.readBytes().toString(Charsets.UTF_8) }

            current = Engine.getTemplate(source)

            document = current
        }

        val writer = StringWriter()

        current!!.evaluate(writer, model)

        return writer.toString()
    }

    private companion object {

        // Cottle does neither escape nor trim, and it does not fail for unknown variables
        val Engine: PebbleEngine = PebbleEngine.Builder()
            .loader(StringLoader())
            .autoEscaping(false)
            .newLineTrimming(false)
            .strictVariables(false)
            .cacheActive(false)
            .build()

    }

}
