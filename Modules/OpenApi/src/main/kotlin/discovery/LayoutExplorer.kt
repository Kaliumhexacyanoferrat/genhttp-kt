package org.genhttp.modules.openapi.discovery

import io.swagger.v3.oas.models.OpenAPI

import org.genhttp.api.content.Handler
import org.genhttp.api.protocol.Request
import org.genhttp.modules.layouting.provider.LayoutHandler
import org.genhttp.modules.openapi.ApiExplorer

/**
 * Explores the handlers routed by a layout.
 */
public class LayoutExplorer : ApiExplorer {

    override fun canExplore(handler: Handler): Boolean = handler is LayoutHandler

    override suspend fun explore(
        request: Request,
        handler: Handler,
        path: List<String>,
        document: OpenAPI,
        schemata: SchemaManager,
        documentation: InheritedDocumentation,
        registry: ApiDiscoveryRegistry
    ) {
        if (handler is LayoutHandler) {
            for (root in handler.rootHandlers) {
                registry.explore(request, root, path, document, schemata, documentation)
            }

            for ((route, routeHandler) in handler.routedHandlers) {
                registry.explore(request, routeHandler, path + route.decode(), document, schemata, documentation)
            }
        }
    }

}
