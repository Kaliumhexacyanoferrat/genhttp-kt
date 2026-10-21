package org.genhttp.modules.controllers

import org.genhttp.modules.controllers.provider.ControllerBuilder

/**
 * Creates controllers, which route requests to their public functions by name
 * (e.g. `fun listUsers()` handles requests to `/list-users/`).
 */
public object Controller {

    /**
     * Creates a controller backed by a new instance of the given type (requires a parameterless constructor).
     */
    public inline fun <reified T : Any> from(): ControllerBuilder = ControllerBuilder().type<T>()

    /**
     * Creates a controller backed by the given instance.
     */
    public fun from(instance: Any): ControllerBuilder = ControllerBuilder().instance(instance)

}
