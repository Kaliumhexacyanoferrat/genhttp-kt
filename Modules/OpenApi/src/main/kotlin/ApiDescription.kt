package org.genhttp.modules.openapi

import org.genhttp.modules.openapi.discovery.ApiDiscoveryRegistryBuilder
import org.genhttp.modules.openapi.handler.OpenApiConcernBuilder

/**
 * Provides a concern that will analyze the inner handler and
 * automatically generate an OpenAPI specification and
 * provide it to the clients when requested.
 */
public object ApiDescription {

    /**
     * Creates a pre-configured concern to be added to a layout or any other handler.
     *
     * The generated concern will crawl through the inner handler chain and analyze the following
     * types of content: Layouts, Concerns, Functional Handlers, Webservices, Controllers.
     * If you use other handlers or specific concerns to provide your API, you will need to implement
     * [ApiExplorer] instances and pass them to the [with] method.
     *
     * @return The pre-configured concern that will generate an OpenAPI specification on request
     */
    public fun create(): OpenApiConcernBuilder = with(ApiDiscovery.default())

    /**
     * Creates a concern that will use the given discovery configuration to search for API endpoints
     * to be added to the generated OpenAPI specification.
     *
     * @param discovery The explorer registry to be used to analyze the handler chain
     * @return The newly generated concern
     */
    public fun with(discovery: ApiDiscoveryRegistryBuilder): OpenApiConcernBuilder = OpenApiConcernBuilder(discovery.build())

}
