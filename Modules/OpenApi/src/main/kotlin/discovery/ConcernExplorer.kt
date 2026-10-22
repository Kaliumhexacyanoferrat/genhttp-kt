package org.genhttp.modules.openapi.discovery

import io.swagger.v3.oas.models.OpenAPI

import org.genhttp.api.content.Concern
import org.genhttp.api.content.Handler
import org.genhttp.api.content.services.DocumentedConcern
import org.genhttp.api.protocol.Request
import org.genhttp.modules.openapi.ApiExplorer

/**
 * Explores the content wrapped by a concern.
 */
public class ConcernExplorer : ApiExplorer {

    override fun canExplore(handler: Handler): Boolean = handler is Concern

    override suspend fun explore(
        request: Request,
        handler: Handler,
        path: List<String>,
        document: OpenAPI,
        schemata: SchemaManager,
        documentation: InheritedDocumentation,
        registry: ApiDiscoveryRegistry
    ) {
        if (handler is Concern) {
            val inner = if (handler is DocumentedConcern) documentation.push(handler) else documentation

            registry.explore(request, handler.content, path, document, schemata, inner)
        }
    }

}
