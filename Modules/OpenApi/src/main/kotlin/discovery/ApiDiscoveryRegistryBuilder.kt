package org.genhttp.modules.openapi.discovery

import org.genhttp.api.infrastructure.Builder
import org.genhttp.modules.openapi.ApiExplorer

import kotlin.reflect.full.createInstance

/**
 * Creates the registry of the explorers used to analyze the handler chain.
 */
public class ApiDiscoveryRegistryBuilder : Builder<ApiDiscoveryRegistry> {

    private val explorers = mutableListOf<ApiExplorer>()

    // region Functionality

    /**
     * Adds the given explorer to the registry.
     *
     * @param TExplorer The type of the explorer to be added (requires a constructor without parameters)
     */
    public inline fun <reified TExplorer : ApiExplorer> add(): ApiDiscoveryRegistryBuilder = add(TExplorer::class.createInstance())

    /**
     * Adds the given explorer to the registry.
     *
     * @param explorer The explorer to be added
     */
    public fun add(explorer: ApiExplorer): ApiDiscoveryRegistryBuilder {
        explorers.add(explorer)
        return this
    }

    override fun build(): ApiDiscoveryRegistry = ApiDiscoveryRegistry(explorers)

    // endregion

}
