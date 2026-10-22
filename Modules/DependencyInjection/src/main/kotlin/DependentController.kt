@file:JvmName("DependentController")

package org.genhttp.modules.dependencyinjection

import org.genhttp.api.infrastructure.Builder
import org.genhttp.modules.controllers.provider.ControllerBuilder
import org.genhttp.modules.conversion.formatters.FormatterRegistry
import org.genhttp.modules.conversion.serializers.SerializationRegistry
import org.genhttp.modules.dependencyinjection.infrastructure.InstanceProvider
import org.genhttp.modules.dependencyinjection.infrastructure.configure
import org.genhttp.modules.layouting.provider.LayoutBuilder
import org.genhttp.modules.reflection.injectors.InjectionRegistryBuilder

import kotlin.reflect.KClass

/**
 * Adds the given controller to the layout with dependency injection enabled.
 *
 * @param T The class implementing the controller
 * @param path The path to register the controller at
 * @param injectors Additional parameter injections to be used by the controller
 * @param serializers Additional serializers to be used by the controller
 * @param formatters Additional formatters to be used by the controller
 * @return The given layout with the specified controller attached
 */
public inline fun <reified T : Any> LayoutBuilder.addDependentController(
    path: String,
    injectors: InjectionRegistryBuilder? = null,
    serializers: Builder<SerializationRegistry>? = null,
    formatters: Builder<FormatterRegistry>? = null
): LayoutBuilder = addDependentController(T::class, path, injectors, serializers, formatters)

@PublishedApi
internal fun <T : Any> LayoutBuilder.addDependentController(
    type: KClass<T>,
    path: String,
    injectors: InjectionRegistryBuilder?,
    serializers: Builder<SerializationRegistry>?,
    formatters: Builder<FormatterRegistry>?
): LayoutBuilder {
    val builder = ControllerBuilder()

    builder.type(type)
    builder.instanceProvider { InstanceProvider.provide(it, type) }

    builder.configure(injectors, serializers, formatters)

    return add(path, builder)
}
