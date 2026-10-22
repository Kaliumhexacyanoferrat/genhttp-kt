package org.genhttp.modules.apibrowsing.common

import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Concerns
import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder

/**
 * Builds a handler that serves a graphical API browser.
 *
 * @param resourceRoot The name of the folder providing the template and static files of the application
 * @param title The initial title of the application
 */
public class BrowserHandlerBuilder(private val resourceRoot: String, title: String) : HandlerBuilder.WithConcerns<BrowserHandlerBuilder> {

    private val concerns = mutableListOf<ConcernBuilder>()

    private var url: String? = null

    private var title: String = title

    /**
     * Sets the URL of the Open API definition to be consumed (defaults to "../openapi.json").
     * Should be relative to avoid issues with CORS etc.
     *
     * @param url The URL the application will fetch the Open API definition from
     */
    public fun url(url: String): BrowserHandlerBuilder {
        this.url = url
        return this
    }

    /**
     * Sets the title of the application that will be rendered by the browser (e.g. the title of the tab).
     *
     * @param title The title of the application to be set
     */
    public fun title(title: String): BrowserHandlerBuilder {
        this.title = title
        return this
    }

    override fun add(concern: ConcernBuilder): BrowserHandlerBuilder {
        concerns.add(concern)
        return this
    }

    override fun build(): Handler {
        val meta = BrowserMetaData(url, title)

        return Concerns.chain(concerns, BrowserHandler(resourceRoot, meta))
    }

}
