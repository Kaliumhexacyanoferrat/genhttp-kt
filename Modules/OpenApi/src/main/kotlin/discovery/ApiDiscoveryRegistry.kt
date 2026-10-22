package org.genhttp.modules.openapi.discovery

import io.swagger.v3.oas.models.OpenAPI

import org.genhttp.api.content.Handler
import org.genhttp.api.protocol.Request
import org.genhttp.modules.openapi.ApiExplorer

/**
 * Holds the explorers used to analyze the handler chain.
 *
 * @param explorers The explorers to be consulted, in the order of their priority
 */
public class ApiDiscoveryRegistry(private val explorers: List<ApiExplorer>) {

    /**
     * Iterates through the registered explorers to find a responsible one to analyze
     * the given handler instance.
     *
     * @param request The currently executed request
     * @param handler The handler to get analyzed
     * @param path The current stack of path segments that have already been analyzed, relative to the location of the OpenAPI concern
     * @param document The document to be adjusted and enriched
     * @param schemata The manager to generate JSON schemas with
     * @param documentation The documentation provided by the concerns wrapping the handler
     */
    public suspend fun explore(
        request: Request,
        handler: Handler,
        path: List<String>,
        document: OpenAPI,
        schemata: SchemaManager,
        documentation: InheritedDocumentation
    ) {
        for (explorer in explorers) {
            if (explorer.canExplore(handler)) {
                explorer.explore(request, handler, path, document, schemata, documentation, this)
                break
            }
        }
    }

}
