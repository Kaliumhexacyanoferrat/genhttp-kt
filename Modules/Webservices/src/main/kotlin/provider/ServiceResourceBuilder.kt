package org.genhttp.modules.webservices.provider

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
import kotlin.reflect.full.createInstance

/**
 * Builds a webservice from a class with functions annotated with
 * [org.genhttp.modules.webservices.ResourceMethod].
 */
public class ServiceResourceBuilder : ReflectionFrameworkBuilder<ServiceResourceBuilder> {

    private val concerns = mutableListOf<ConcernBuilder>()

    private var type: KClass<*>? = null

    private var instanceProvider: (suspend (Request) -> Any)? = null

    private var formatters: Builder<FormatterRegistry>? = null

    private var injectors: Builder<InjectionRegistry>? = null

    private var serializers: Builder<SerializationRegistry>? = null

    private var executionMode: ExecutionMode? = null

    // region Functionality

    /**
     * Uses a new instance of the given type (requires a parameterless constructor).
     */
    public inline fun <reified T : Any> type(): ServiceResourceBuilder = instance(T::class.createInstance())

    /**
     * Sets the type of the service (the instance needs to be provided via [instanceProvider]).
     */
    public fun type(type: KClass<*>): ServiceResourceBuilder {
        this.type = type
        return this
    }

    /**
     * Uses the given instance to handle requests.
     */
    public fun instance(instance: Any): ServiceResourceBuilder {
        type = instance::class
        instanceProvider = FixedInstance(instance)
        return this
    }

    /**
     * Sets a function that provides the instance to handle a request with (e.g. per request).
     */
    public fun instanceProvider(provider: suspend (Request) -> Any): ServiceResourceBuilder {
        instanceProvider = provider
        return this
    }

    override fun serializers(registry: Builder<SerializationRegistry>): ServiceResourceBuilder {
        serializers = registry
        return this
    }

    override fun injectors(registry: Builder<InjectionRegistry>): ServiceResourceBuilder {
        injectors = registry
        return this
    }

    override fun formatters(registry: Builder<FormatterRegistry>): ServiceResourceBuilder {
        formatters = registry
        return this
    }

    override fun executionMode(mode: ExecutionMode): ServiceResourceBuilder {
        executionMode = mode
        return this
    }

    override fun add(concern: ConcernBuilder): ServiceResourceBuilder {
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

        return Concerns.chain(concerns, ServiceResourceRouter(type, instanceProvider, ExecutionSettings(executionMode), registry))
    }

    // endregion

    /**
     * Provides the same instance for every request (unlike a suspend lambda, invoking it does not allocate).
     */
    private class FixedInstance(private val instance: Any) : suspend (Request) -> Any {

        override suspend fun invoke(request: Request): Any = instance

    }

}
