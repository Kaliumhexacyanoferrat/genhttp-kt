package org.genhttp.modules.controllers.provider

import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Concerns
import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Builder
import org.genhttp.api.infrastructure.BuilderMissingPropertyException
import org.genhttp.api.protocol.Request
import org.genhttp.modules.conversion.Formatting
import org.genhttp.modules.conversion.Serialization
import org.genhttp.modules.conversion.formatters.FormatterRegistry
import org.genhttp.modules.conversion.serializers.SerializationRegistry
import org.genhttp.modules.reflection.ExecutionMode
import org.genhttp.modules.reflection.ExecutionSettings
import org.genhttp.modules.reflection.Injection
import org.genhttp.modules.reflection.MethodRegistry
import org.genhttp.modules.reflection.ReflectionFrameworkBuilder
import org.genhttp.modules.reflection.injectors.InjectionRegistry

import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.isAccessible

/**
 * Builds a controller from a class, routing requests to its public functions by name.
 *
 */
public class ControllerBuilder : ReflectionFrameworkBuilder<ControllerBuilder> {

    private val concerns = mutableListOf<ConcernBuilder>()

    private var type: KClass<*>? = null

    private var instanceProvider: (suspend (Request) -> Any)? = null

    private var formatters: Builder<FormatterRegistry>? = null

    private var injectors: Builder<InjectionRegistry>? = null

    private var serializers: Builder<SerializationRegistry>? = null

    private var executionMode: ExecutionMode? = null

    // region Functionality

    /**
     * Uses a new instance of the given type (requires a parameterless constructor, which
     * does not need to be accessible, like `new T()` of the caller upstream).
     */
    public inline fun <reified T : Any> type(): ControllerBuilder = instance(newInstance(T::class))

    @PublishedApi
    internal fun newInstance(type: KClass<*>): Any {
        val constructor = type.constructors.singleOrNull { it.parameters.all(KParameter::isOptional) }
            ?: throw IllegalArgumentException("Class '${type.simpleName}' needs a single constructor without required parameters")

        constructor.isAccessible = true

        return constructor.callBy(emptyMap())
    }

    /**
     * Sets the type of the controller (the instance needs to be provided via [instanceProvider]).
     */
    public fun type(type: KClass<*>): ControllerBuilder {
        this.type = type
        return this
    }

    /**
     * Uses the given instance to handle requests.
     */
    public fun instance(instance: Any): ControllerBuilder {
        type = instance::class
        instanceProvider = FixedInstance(instance)
        return this
    }

    /**
     * Sets a function that provides the instance to handle a request with (e.g. per request).
     */
    public fun instanceProvider(provider: suspend (Request) -> Any): ControllerBuilder {
        instanceProvider = provider
        return this
    }

    override fun serializers(registry: Builder<SerializationRegistry>): ControllerBuilder {
        serializers = registry
        return this
    }

    override fun injectors(registry: Builder<InjectionRegistry>): ControllerBuilder {
        injectors = registry
        return this
    }

    override fun formatters(registry: Builder<FormatterRegistry>): ControllerBuilder {
        formatters = registry
        return this
    }

    override fun executionMode(mode: ExecutionMode): ControllerBuilder {
        executionMode = mode
        return this
    }

    override fun add(concern: ConcernBuilder): ControllerBuilder {
        concerns.add(concern)
        return this
    }

    override fun build(): Handler {
        val serializers = (serializers ?: Serialization.default()).build()
        val injectors = (injectors ?: Injection.default()).build()
        val formatters = (formatters ?: Formatting.default()).build()

        val instanceProvider = instanceProvider ?: throw BuilderMissingPropertyException("Instance provider has not been set")
        val type = type ?: throw BuilderMissingPropertyException("Type has not been set")

        val registry = MethodRegistry(serializers, injectors, formatters)

        return Concerns.chain(concerns, ControllerHandler(type, instanceProvider, ExecutionSettings(executionMode), registry))
    }

    // endregion

    /**
     * Provides the same instance for every request (unlike a suspend lambda, invoking it does not allocate).
     */
    private class FixedInstance(private val instance: Any) : suspend (Request) -> Any {

        override suspend fun invoke(request: Request): Any = instance

    }

}
