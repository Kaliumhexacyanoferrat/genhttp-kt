package org.genhttp.modules.openapi.discovery

import io.swagger.v3.oas.models.OpenAPI

import org.genhttp.api.content.Handler
import org.genhttp.api.protocol.Request
import org.genhttp.modules.openapi.ApiExplorer
import org.genhttp.modules.reflection.MethodCollection

/**
 * Explores the operations of a service.
 */
public class MethodCollectionExplorer : ApiExplorer {

    override fun canExplore(handler: Handler): Boolean = handler is MethodCollection

    override suspend fun explore(
        request: Request,
        handler: Handler,
        path: List<String>,
        document: OpenAPI,
        schemata: SchemaManager,
        documentation: InheritedDocumentation,
        registry: ApiDiscoveryRegistry
    ) {
        if (handler is MethodCollection) {
            for (method in handler.methods) {
                registry.explore(request, method, path, document, schemata, documentation)
            }
        }
    }

}
