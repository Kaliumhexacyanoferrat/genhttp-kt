package org.genhttp.modules.reflection

import org.genhttp.modules.reflection.injectors.BodyArgumentsInjector
import org.genhttp.modules.reflection.injectors.HandlerInjector
import org.genhttp.modules.reflection.injectors.InjectionRegistryBuilder
import org.genhttp.modules.reflection.injectors.RequestInjector

/**
 * Factory for injection registries.
 */
public object Injection {

    /**
     * Creates an empty injection registry.
     */
    public fun empty(): InjectionRegistryBuilder = InjectionRegistryBuilder()

    /**
     * Creates a registry injecting the request, the handler and form encoded body arguments.
     */
    public fun default(): InjectionRegistryBuilder = InjectionRegistryBuilder()
        .add(RequestInjector())
        .add(HandlerInjector())
        .add(BodyArgumentsInjector())

}
