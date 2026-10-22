package org.genhttp.modules.openapi

import io.swagger.v3.oas.models.OpenAPI

import org.genhttp.api.content.Handler
import org.genhttp.api.protocol.Request
import org.genhttp.modules.openapi.discovery.ApiDiscoveryRegistry
import org.genhttp.modules.openapi.discovery.InheritedDocumentation
import org.genhttp.modules.openapi.discovery.SchemaManager

/**
 * Allows to analyze a specific kind of handler and adds the API endpoints defined
 * by the handler to the resulting OpenAPI document.
 */
public interface ApiExplorer {

    /**
     * Specifies, whether the given handler is supported by this explorer.
     *
     * Note that this method is only allowed to return true if the explorer can actually
     * analyze this handler. No other explorer will be invoked for the handler in this case.
     *
     * @param handler The handler to be inspected
     * @return true, if this explorer is capable of inspecting this explorer
     */
    public fun canExplore(handler: Handler): Boolean

    /**
     * Analyzes the given handler and adds the endpoints defined by this handler to the resulting
     * OpenAPI document.
     *
     * @param request The currently executed request
     * @param handler The handler to be analyzed
     * @param path The current stack of path segments that have already been analyzed, relative to the location of the OpenAPI concern
     * @param document The document to be adjusted and enriched
     * @param schemata The manager to generate JSON schemas with
     * @param documentation The documentation provided by the concerns wrapping the handler, to be passed when exploring child handlers
     * @param registry The registry containing all active explorers which can be used to further analyze any child handler of the given handler instance
     */
    public suspend fun explore(
        request: Request,
        handler: Handler,
        path: List<String>,
        document: OpenAPI,
        schemata: SchemaManager,
        documentation: InheritedDocumentation,
        registry: ApiDiscoveryRegistry
    )

}
