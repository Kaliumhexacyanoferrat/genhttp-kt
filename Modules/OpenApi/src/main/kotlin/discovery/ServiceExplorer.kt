package org.genhttp.modules.openapi.discovery

import io.swagger.v3.oas.models.OpenAPI

import org.genhttp.api.content.Handler
import org.genhttp.api.protocol.Request
import org.genhttp.modules.openapi.ApiExplorer
import org.genhttp.modules.reflection.ServiceMethodProvider

/**
 * Explores handlers providing the methods of a service (webservices, controllers, inline handlers).
 */
public class ServiceExplorer : ApiExplorer {

    override fun canExplore(handler: Handler): Boolean = handler is ServiceMethodProvider

    override suspend fun explore(
        request: Request,
        handler: Handler,
        path: List<String>,
        document: OpenAPI,
        schemata: SchemaManager,
        documentation: InheritedDocumentation,
        registry: ApiDiscoveryRegistry
    ) {
        if (handler is ServiceMethodProvider) {
            registry.explore(request, handler.methods, path, document, schemata, documentation)
        }
    }

}
