@file:JvmName("Extensions")

package org.genhttp.modules.webservices

import org.genhttp.api.infrastructure.Builder
import org.genhttp.modules.conversion.formatters.FormatterRegistry
import org.genhttp.modules.conversion.serializers.SerializationRegistry
import org.genhttp.modules.layouting.provider.LayoutBuilder
import org.genhttp.modules.reflection.ExecutionMode
import org.genhttp.modules.reflection.injectors.InjectionRegistry
import org.genhttp.modules.webservices.provider.ServiceResourceBuilder

/**
 * Adds a webservice backed by a new instance of the given type to the layout.
 *
 * @param path The path the service is reachable at
 * @param injectors The injectors to be used by the service, if not the default ones
 * @param serializers The serialization formats to be used by the service, if not the default ones
 * @param formatters The formatters to be used by the service, if not the default ones
 * @param mode The execution mode of the service, if not the default
 */
public inline fun <reified T : Any> LayoutBuilder.addService(
    path: String,
    injectors: Builder<InjectionRegistry>? = null,
    serializers: Builder<SerializationRegistry>? = null,
    formatters: Builder<FormatterRegistry>? = null,
    mode: ExecutionMode? = null
): LayoutBuilder = add(path, ServiceResource.from<T>().configured(injectors, serializers, formatters, mode))

/**
 * Adds a webservice backed by the given instance to the layout.
 *
 * @param path The path the service is reachable at
 * @param instance The instance handling the requests
 * @param injectors The injectors to be used by the service, if not the default ones
 * @param serializers The serialization formats to be used by the service, if not the default ones
 * @param formatters The formatters to be used by the service, if not the default ones
 * @param mode The execution mode of the service, if not the default
 */
public fun LayoutBuilder.addService(
    path: String,
    instance: Any,
    injectors: Builder<InjectionRegistry>? = null,
    serializers: Builder<SerializationRegistry>? = null,
    formatters: Builder<FormatterRegistry>? = null,
    mode: ExecutionMode? = null
): LayoutBuilder = add(path, ServiceResource.from(instance).configured(injectors, serializers, formatters, mode))

/**
 * Applies the given (optional) configuration to the builder.
 */
@PublishedApi
internal fun ServiceResourceBuilder.configured(
    injectors: Builder<InjectionRegistry>?,
    serializers: Builder<SerializationRegistry>?,
    formatters: Builder<FormatterRegistry>?,
    mode: ExecutionMode?
): ServiceResourceBuilder {
    injectors?.let { injectors(it) }
    serializers?.let { serializers(it) }
    formatters?.let { formatters(it) }
    mode?.let { executionMode(it) }

    return this
}
