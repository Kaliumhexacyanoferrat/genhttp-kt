@file:JvmName("DependentWebservice")

package org.genhttp.modules.dependencyinjection

import org.genhttp.api.infrastructure.Builder
import org.genhttp.modules.conversion.formatters.FormatterRegistry
import org.genhttp.modules.conversion.serializers.SerializationRegistry
import org.genhttp.modules.dependencyinjection.infrastructure.InstanceProvider
import org.genhttp.modules.dependencyinjection.infrastructure.configure
import org.genhttp.modules.layouting.provider.LayoutBuilder
import org.genhttp.modules.reflection.injectors.InjectionRegistryBuilder
import org.genhttp.modules.webservices.provider.ServiceResourceBuilder

import kotlin.reflect.KClass

/**
 * Adds the given webservice to the layout with dependency injection enabled.
 *
 * @param T The class implementing the webservice
 * @param path The path to register the webservice at
 * @param injectors Additional parameter injections to be used by the webservice
 * @param serializers Additional serializers to be used by the webservice
 * @param formatters Additional formatters to be used by the webservice
 * @return The given layout with the specified webservice attached
 */
public inline fun <reified T : Any> LayoutBuilder.addDependentService(
    path: String,
    injectors: InjectionRegistryBuilder? = null,
    serializers: Builder<SerializationRegistry>? = null,
    formatters: Builder<FormatterRegistry>? = null
): LayoutBuilder = addDependentService(T::class, path, injectors, serializers, formatters)

@PublishedApi
internal fun <T : Any> LayoutBuilder.addDependentService(
    type: KClass<T>,
    path: String,
    injectors: InjectionRegistryBuilder?,
    serializers: Builder<SerializationRegistry>?,
    formatters: Builder<FormatterRegistry>?
): LayoutBuilder {
    val builder = ServiceResourceBuilder()

    builder.type(type)
    builder.instanceProvider { InstanceProvider.provide(it, type) }

    builder.configure(injectors, serializers, formatters)

    return add(path, builder)
}
