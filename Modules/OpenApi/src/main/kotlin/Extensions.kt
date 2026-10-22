@file:JvmName("Extensions")

package org.genhttp.modules.openapi

import org.genhttp.api.content.HandlerBuilder
import org.genhttp.modules.openapi.discovery.ApiDiscoveryRegistryBuilder

/**
 * Adds a pre-configured concern to the given builder.
 *
 * The generated concern will crawl through the inner handler chain and analyze the following
 * types of content: Layouts, Concerns, Functional Handlers, Webservices, Controllers.
 */
public fun <T : HandlerBuilder.WithConcerns<T>> T.addOpenApi(): T = addOpenApi(ApiDiscovery.default())

/**
 * Creates a concern that will use the given discovery configuration to search for API endpoints
 * to be added to the generated OpenAPI specification.
 *
 * @param registry The explorer registry to be used to analyze the handler chain
 */
public fun <T : HandlerBuilder.WithConcerns<T>> T.addOpenApi(registry: ApiDiscoveryRegistryBuilder): T {
    add(ApiDescription.with(registry))
    return this
}
