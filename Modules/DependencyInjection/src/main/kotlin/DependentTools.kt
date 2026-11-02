package org.genhttp.modules.dependencyinjection

import org.genhttp.modules.dependencyinjection.infrastructure.DependencyInjector
import org.genhttp.modules.dependencyinjection.infrastructure.InstanceProvider
import org.genhttp.modules.mcp.Tools
import org.genhttp.modules.mcp.provider.ToolsHandlerBuilder
import org.genhttp.modules.reflection.Injection
import org.genhttp.modules.reflection.injectors.InjectionRegistryBuilder

/**
 * Creates endpoints providing tools to AI agents with dependency injection enabled.
 */
public object DependentTools {

    /**
     * Creates an endpoint providing tools to AI agents, with tool classes being created per call with their
     * dependencies resolved from the service scope of the request, and services being injected into the
     * parameters of tools.
     *
     * @param injectors The injectors to be used in addition to dependency injection
     * @return The newly created endpoint
     */
    public fun create(injectors: InjectionRegistryBuilder? = null): ToolsHandlerBuilder {
        val registry = injectors ?: Injection.default()

        registry.add(DependencyInjector())

        return Tools.create()
            .injectors(registry)
            .instanceProvider(InstanceProvider::provide)
    }

}
