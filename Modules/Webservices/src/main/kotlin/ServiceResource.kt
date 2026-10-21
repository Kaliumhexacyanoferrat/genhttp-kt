package org.genhttp.modules.webservices

import org.genhttp.modules.webservices.provider.ServiceResourceBuilder

/**
 * Creates webservices from classes with functions annotated with [ResourceMethod].
 */
public object ServiceResource {

    /**
     * Creates a webservice backed by a new instance of the given type (requires a parameterless constructor).
     */
    public inline fun <reified T : Any> from(): ServiceResourceBuilder = ServiceResourceBuilder().type<T>()

    /**
     * Creates a webservice backed by the given instance.
     */
    public fun from(instance: Any): ServiceResourceBuilder = ServiceResourceBuilder().instance(instance)

}
