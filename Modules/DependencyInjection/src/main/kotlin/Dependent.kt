package org.genhttp.modules.dependencyinjection

import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.modules.dependencyinjection.basics.ConcernIntegrationBuilder
import org.genhttp.modules.dependencyinjection.basics.HandlerIntegrationBuilder

import kotlin.reflect.KClass

/**
 * Allows to enable dependency injection for concerns and handlers.
 */
public object Dependent {

    /**
     * Allows the given class to be used as a regular concern with dependency injection enabled.
     *
     * @param T The class implementing the concern
     */
    public inline fun <reified T : DependentConcern> concern(): ConcernBuilder = concern(T::class)

    /**
     * Allows the given class to be used as a regular concern with dependency injection enabled.
     *
     * @param type The class implementing the concern
     */
    public fun <T : DependentConcern> concern(type: KClass<T>): ConcernBuilder = ConcernIntegrationBuilder(type)

    /**
     * Allows the given class to be used as a regular handler with dependency injection enabled.
     *
     * @param T The class implementing the handler
     */
    public inline fun <reified T : DependentHandler> handler(): HandlerBuilder = handler(T::class)

    /**
     * Allows the given class to be used as a regular handler with dependency injection enabled.
     *
     * @param type The class implementing the handler
     */
    public fun <T : DependentHandler> handler(type: KClass<T>): HandlerBuilder = HandlerIntegrationBuilder(type)

}
