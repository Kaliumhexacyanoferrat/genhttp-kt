@file:JvmName("Extensions")

package org.genhttp.modules.controllers

import org.genhttp.api.infrastructure.Builder
import org.genhttp.modules.controllers.provider.ControllerBuilder
import org.genhttp.modules.conversion.formatters.FormatterRegistry
import org.genhttp.modules.conversion.serializers.SerializationRegistry
import org.genhttp.modules.layouting.provider.LayoutBuilder
import org.genhttp.modules.reflection.ExecutionMode
import org.genhttp.modules.reflection.injectors.InjectionRegistry

/**
 * Adds a controller backed by a new instance of the given type to the layout.
 *
 * @param path The path the controller is reachable at
 * @param injectors The injectors to be used by the controller, if not the default ones
 * @param serializers The serialization formats to be used by the controller, if not the default ones
 * @param formatters The formatters to be used by the controller, if not the default ones
 * @param mode The execution mode of the controller, if not the default
 */
public inline fun <reified T : Any> LayoutBuilder.addController(
    path: String,
    injectors: Builder<InjectionRegistry>? = null,
    serializers: Builder<SerializationRegistry>? = null,
    formatters: Builder<FormatterRegistry>? = null,
    mode: ExecutionMode? = null
): LayoutBuilder {
    add(path, Controller.from<T>().configured(injectors, serializers, formatters, mode))
    return this
}

/**
 * Adds a controller backed by a new instance of the given type as a root handler of the layout.
 *
 * @param injectors The injectors to be used by the controller, if not the default ones
 * @param serializers The serialization formats to be used by the controller, if not the default ones
 * @param formatters The formatters to be used by the controller, if not the default ones
 * @param mode The execution mode of the controller, if not the default
 */
public inline fun <reified T : Any> LayoutBuilder.indexController(
    injectors: Builder<InjectionRegistry>? = null,
    serializers: Builder<SerializationRegistry>? = null,
    formatters: Builder<FormatterRegistry>? = null,
    mode: ExecutionMode? = null
): LayoutBuilder {
    add(Controller.from<T>().configured(injectors, serializers, formatters, mode))
    return this
}

/**
 * Applies the given (optional) configuration to the builder.
 */
@PublishedApi
internal fun ControllerBuilder.configured(
    injectors: Builder<InjectionRegistry>?,
    serializers: Builder<SerializationRegistry>?,
    formatters: Builder<FormatterRegistry>?,
    mode: ExecutionMode?
): ControllerBuilder {
    injectors?.let { injectors(it) }
    serializers?.let { serializers(it) }
    formatters?.let { formatters(it) }
    mode?.let { executionMode(it) }

    return this
}
