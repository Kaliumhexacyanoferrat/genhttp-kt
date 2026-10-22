package org.genhttp.modules.openapi

import org.genhttp.modules.openapi.discovery.ApiDiscoveryRegistryBuilder
import org.genhttp.modules.openapi.discovery.ConcernExplorer
import org.genhttp.modules.openapi.discovery.LayoutExplorer
import org.genhttp.modules.openapi.discovery.MethodCollectionExplorer
import org.genhttp.modules.openapi.discovery.MethodHandlerExplorer
import org.genhttp.modules.openapi.discovery.ServiceExplorer

/**
 * Provides capabilities used by the OpenAPI generation feature to analyze
 * the functionality provided by your API.
 */
public object ApiDiscovery {

    /**
     * Creates an empty registry.
     *
     * @return The newly created, empty registry
     */
    public fun empty(): ApiDiscoveryRegistryBuilder = ApiDiscoveryRegistryBuilder()

    /**
     * Creates a registry that supports layouts, concerns, functional handlers,
     * controllers and webservices for automatic content discovery.
     *
     * @return The default registry to use as a basis
     */
    public fun default(): ApiDiscoveryRegistryBuilder = empty().add<ConcernExplorer>()
        .add<LayoutExplorer>()
        .add<ServiceExplorer>()
        .add<MethodCollectionExplorer>()
        .add<MethodHandlerExplorer>()

}
