package org.genhttp.modules.functional.provider

import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Concerns
import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Builder
import org.genhttp.api.protocol.RequestMethod
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

import kotlin.reflect.KFunction

/**
 * Builds a service from lambdas or function references (`Inline.create()`).
 *
 * Lambdas must be suspend lambdas with explicitly typed parameters, so that their parameter names can be read:
 *
 * ```kotlin
 * Inline.create()
 *     .get { a: Int, b: Int -> a + b }
 *     .post(":id", ::update)
 * ```
 *
 * Parameters that need annotations (such as `@FromBody`) or default values are declared using function references,
 * as Kotlin does not allow them on lambda parameters. Overloads for lambdas with parameters are provided as
 * extension functions (`org.genhttp.modules.functional.*`).
 */
public class InlineBuilder : ReflectionFrameworkBuilder<InlineBuilder> {

    private val concerns = mutableListOf<ConcernBuilder>()

    private val functions = mutableListOf<InlineFunction>()

    private var formatters: Builder<FormatterRegistry>? = null

    private var injectors: Builder<InjectionRegistry>? = null

    private var serializers: Builder<SerializationRegistry>? = null

    private var executionMode: ExecutionMode? = null

    // region Configuration

    override fun serializers(registry: Builder<SerializationRegistry>): InlineBuilder {
        serializers = registry
        return this
    }

    override fun injectors(registry: Builder<InjectionRegistry>): InlineBuilder {
        injectors = registry
        return this
    }

    override fun formatters(registry: Builder<FormatterRegistry>): InlineBuilder {
        formatters = registry
        return this
    }

    override fun executionMode(mode: ExecutionMode): InlineBuilder {
        executionMode = mode
        return this
    }

    override fun add(concern: ConcernBuilder): InlineBuilder {
        concerns.add(concern)
        return this
    }

    // endregion

    // region Handlers without parameters

    /** Adds a handler for requests of any method. */
    public fun <R> any(function: suspend () -> R): InlineBuilder = anyFunction(null, function)

    /** Adds a handler for requests of any method below the given path. */
    public fun <R> any(path: String, function: suspend () -> R): InlineBuilder = anyFunction(path, function)

    /** Adds a GET handler (also answering HEAD requests). */
    public fun <R> get(function: suspend () -> R): InlineBuilder = getFunction(null, function)

    /** Adds a GET handler (also answering HEAD requests) for the given path. */
    public fun <R> get(path: String, function: suspend () -> R): InlineBuilder = getFunction(path, function)

    /** Adds a HEAD handler. */
    public fun <R> head(function: suspend () -> R): InlineBuilder = headFunction(null, function)

    /** Adds a HEAD handler for the given path. */
    public fun <R> head(path: String, function: suspend () -> R): InlineBuilder = headFunction(path, function)

    /** Adds a POST handler. */
    public fun <R> post(function: suspend () -> R): InlineBuilder = postFunction(null, function)

    /** Adds a POST handler for the given path. */
    public fun <R> post(path: String, function: suspend () -> R): InlineBuilder = postFunction(path, function)

    /** Adds a PUT handler. */
    public fun <R> put(function: suspend () -> R): InlineBuilder = putFunction(null, function)

    /** Adds a PUT handler for the given path. */
    public fun <R> put(path: String, function: suspend () -> R): InlineBuilder = putFunction(path, function)

    /** Adds a DELETE handler. */
    public fun <R> delete(function: suspend () -> R): InlineBuilder = deleteFunction(null, function)

    /** Adds a DELETE handler for the given path. */
    public fun <R> delete(path: String, function: suspend () -> R): InlineBuilder = deleteFunction(path, function)

    // endregion

    // region Function references

    /** Adds a handler for requests of any method, implemented by the given function. */
    public fun any(function: KFunction<*>): InlineBuilder = anyFunction(null, function)

    /** Adds a handler for requests of any method below the given path, implemented by the given function. */
    public fun any(path: String, function: KFunction<*>): InlineBuilder = anyFunction(path, function)

    /** Adds a GET handler (also answering HEAD requests), implemented by the given function. */
    public fun get(function: KFunction<*>): InlineBuilder = getFunction(null, function)

    /** Adds a GET handler (also answering HEAD requests) for the given path, implemented by the given function. */
    public fun get(path: String, function: KFunction<*>): InlineBuilder = getFunction(path, function)

    /** Adds a HEAD handler, implemented by the given function. */
    public fun head(function: KFunction<*>): InlineBuilder = headFunction(null, function)

    /** Adds a HEAD handler for the given path, implemented by the given function. */
    public fun head(path: String, function: KFunction<*>): InlineBuilder = headFunction(path, function)

    /** Adds a POST handler, implemented by the given function. */
    public fun post(function: KFunction<*>): InlineBuilder = postFunction(null, function)

    /** Adds a POST handler for the given path, implemented by the given function. */
    public fun post(path: String, function: KFunction<*>): InlineBuilder = postFunction(path, function)

    /** Adds a PUT handler, implemented by the given function. */
    public fun put(function: KFunction<*>): InlineBuilder = putFunction(null, function)

    /** Adds a PUT handler for the given path, implemented by the given function. */
    public fun put(path: String, function: KFunction<*>): InlineBuilder = putFunction(path, function)

    /** Adds a DELETE handler, implemented by the given function. */
    public fun delete(function: KFunction<*>): InlineBuilder = deleteFunction(null, function)

    /** Adds a DELETE handler for the given path, implemented by the given function. */
    public fun delete(path: String, function: KFunction<*>): InlineBuilder = deleteFunction(path, function)

    // endregion

    // region Generic registration

    /**
     * Adds a handler for the given request methods.
     *
     * @param function A suspend lambda with typed parameters or a function reference
     * @param methods The request methods to handle (a set containing only GET also handles HEAD)
     * @param path The path the handler is reachable at, if any
     */
    public fun on(function: Function<*>, methods: Set<RequestMethod>? = null, path: String? = null): InlineBuilder {
        val requestMethods = methods?.toMutableSet() ?: mutableSetOf()

        if (requestMethods.size == 1 && requestMethods.contains(RequestMethod.Get)) {
            requestMethods.add(RequestMethod.Head)
        }

        val actualPath = if (path?.startsWith('/') == true) path.substring(1) else path

        functions.add(InlineFunction(actualPath, InlineMethodConfiguration(requestMethods), function))

        return this
    }

    internal fun anyFunction(path: String?, function: Function<*>): InlineBuilder = on(function, AllMethods, path)

    internal fun getFunction(path: String?, function: Function<*>): InlineBuilder = on(function, setOf(RequestMethod.Get), path)

    internal fun headFunction(path: String?, function: Function<*>): InlineBuilder = on(function, setOf(RequestMethod.Head), path)

    internal fun postFunction(path: String?, function: Function<*>): InlineBuilder = on(function, setOf(RequestMethod.Post), path)

    internal fun putFunction(path: String?, function: Function<*>): InlineBuilder = on(function, setOf(RequestMethod.Put), path)

    internal fun deleteFunction(path: String?, function: Function<*>): InlineBuilder = on(function, setOf(RequestMethod.Delete), path)

    // endregion

    override fun build(): Handler {
        val serializers = (serializers ?: Serialization.default()).build()
        val injectors = (injectors ?: Injection.default()).build()
        val formatters = (formatters ?: Formatting.default()).build()

        val registry = MethodRegistry(serializers, injectors, formatters)

        return Concerns.chain(concerns, InlineHandler(functions.toList(), registry, ExecutionSettings(executionMode)))
    }

    private companion object {

        val AllMethods: Set<RequestMethod> = setOf(
            RequestMethod.Connect,
            RequestMethod.Delete,
            RequestMethod.Get,
            RequestMethod.Head,
            RequestMethod.Options,
            RequestMethod.Patch,
            RequestMethod.Post,
            RequestMethod.Put,
            RequestMethod.Trace
        )

    }

}
