package org.genhttp.modules.openapi.handler

import io.swagger.v3.oas.models.OpenAPI

import org.genhttp.api.content.Concern
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.api.protocol.Request
import org.genhttp.modules.openapi.discovery.ApiDiscoveryRegistry

/**
 * Creates the concern that generates the OpenAPI specification.
 *
 * @param registry The registry of the explorers used to analyze the handler chain
 */
public class OpenApiConcernBuilder(private val registry: ApiDiscoveryRegistry) : ConcernBuilder {

    private var caching = true

    private var postProcessor: ((Request, OpenAPI) -> Unit)? = null

    private var title: String? = null

    private var version: String? = null

    // region Functionality

    /**
     * Sets the title of the OpenAPI specification.
     *
     * @param title The title of the API
     */
    public fun title(title: String): OpenApiConcernBuilder {
        this.title = title
        return this
    }

    /**
     * Sets the version of the described API.
     *
     * @param version The version of the API
     */
    public fun version(version: String): OpenApiConcernBuilder {
        this.version = version
        return this
    }

    /**
     * Specifies, whether the generated OpenAPI specification should
     * get cached on first request, so it is no re-generated on every request.
     *
     * @param enabled Whether to use caching or not
     */
    public fun caching(enabled: Boolean): OpenApiConcernBuilder {
        caching = enabled
        return this
    }

    /**
     * Registers a function that will be called when an OpenAPI document has been
     * generated, directly before it is served to the client.
     *
     * @param action The method to be invoked to adjust the generated document
     */
    public fun postProcessor(action: (Request, OpenAPI) -> Unit): OpenApiConcernBuilder {
        postProcessor = action
        return this
    }

    override fun build(content: Handler): Concern = OpenApiConcern(content, registry, caching, ::doPostProcessing)

    private fun doPostProcessing(request: Request, document: OpenAPI) {
        title?.let { document.info.title = it }

        version?.let { document.info.version = it }

        postProcessor?.invoke(request, document)
    }

    // endregion

}
