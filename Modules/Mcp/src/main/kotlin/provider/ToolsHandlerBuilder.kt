package org.genhttp.modules.mcp.provider

import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Concerns
import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.Builder
import org.genhttp.api.protocol.Request
import org.genhttp.modules.conversion.Formatting
import org.genhttp.modules.conversion.Serialization
import org.genhttp.modules.mcp.schema.McpSerialization
import org.genhttp.modules.reflection.Injection
import org.genhttp.modules.reflection.MethodRegistry
import org.genhttp.modules.reflection.injectors.InjectionRegistry

import tools.jackson.databind.json.JsonMapper

import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.isAccessible
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Configures an endpoint that provides tools to AI agents via the Model Context Protocol.
 *
 * Tools implemented by lambdas with parameters are added via the extension functions of the module package
 * (`import org.genhttp.modules.mcp.*`).
 */
public class ToolsHandlerBuilder : HandlerBuilder.WithConcerns<ToolsHandlerBuilder> {

    private val concerns = mutableListOf<ConcernBuilder>()

    private val tools = mutableListOf<MethodToolDefinition>()

    private val origins = mutableListOf<String>()

    private var identity: ServerIdentity? = null

    private var instructions: String? = null

    private var cacheDuration: Duration = 5.minutes

    private var serialization: JsonMapper? = null

    private var injectors: Builder<InjectionRegistry>? = null

    private var instanceProvider: (suspend (KClass<*>, Request) -> Any)? = null

    // region Functionality

    /**
     * Adds the functions of the given class annotated with [org.genhttp.modules.mcp.Tool] as tools.
     *
     * The parameters of a function become the arguments of the tool, except for values injected by the framework
     * (such as the request or the authenticated user). Interceptors such as role checks are executed before the
     * function is invoked. Classes that also provide webservice or controller methods are not supported.
     *
     * The instance the tools are invoked on is created by the configured [instanceProvider], or once via its
     * parameterless constructor. Functions of the companion object are invoked on the companion object.
     *
     * @param T The class providing the tools
     */
    public inline fun <reified T : Any> add(): ToolsHandlerBuilder = add(T::class)

    /**
     * Adds the functions of the given class annotated with [org.genhttp.modules.mcp.Tool] as tools (see [add]).
     */
    @PublishedApi
    internal fun add(type: KClass<*>): ToolsHandlerBuilder {
        tools.addAll(MethodToolDefinition.discover(type, null))
        return this
    }

    /**
     * Adds the functions of the given instance annotated with [org.genhttp.modules.mcp.Tool] as tools.
     *
     * @param instance The instance providing the tools
     */
    public fun add(instance: Any): ToolsHandlerBuilder {
        tools.addAll(MethodToolDefinition.discover(instance::class) { instance })
        return this
    }

    /**
     * Adds a tool implemented by the given function reference.
     *
     * The parameters of the function become the arguments of the tool, except for values injected by the
     * framework. Annotations on the function (such as role checks or [org.genhttp.modules.mcp.Tool] to specify
     * the title and hints of the tool) and its parameters (such as descriptions) are respected.
     *
     * @param name The name of the tool
     * @param description Describes the purpose of the tool to the model
     * @param function The function implementing the tool (a function reference, such as `::add`, or a bound
     * reference, such as `tools::add`)
     */
    public fun add(name: String, description: String, function: KFunction<*>): ToolsHandlerBuilder = addFunction(name, description, function)

    /**
     * Adds a tool without arguments implemented by the given lambda.
     *
     * @param name The name of the tool
     * @param description Describes the purpose of the tool to the model
     * @param function The lambda implementing the tool
     */
    public fun <R> add(name: String, description: String, function: suspend () -> R): ToolsHandlerBuilder = addFunction(name, description, function)

    /**
     * Adds a concern (such as authentication) to the endpoint.
     *
     * @param concern The concern to be added
     */
    override fun add(concern: ConcernBuilder): ToolsHandlerBuilder {
        concerns.add(concern)
        return this
    }

    /**
     * Sets the logic used to create the instances tools added via [add] are invoked on, e.g. per request via
     * dependency injection.
     *
     * The provider is only invoked if the arguments of a call are valid and no interceptor objected.
     *
     * @param provider Creates the instance of the given type to invoke a tool on
     */
    public fun instanceProvider(provider: suspend (KClass<*>, Request) -> Any): ToolsHandlerBuilder {
        instanceProvider = provider
        return this
    }

    /**
     * Sets the injectors used to provide framework values (such as the request or the authenticated user) to
     * tools implemented by functions or lambdas.
     *
     * @param registry The injectors to be used
     */
    public fun injectors(registry: Builder<InjectionRegistry>): ToolsHandlerBuilder {
        injectors = registry
        return this
    }

    /**
     * Identifies this server to clients.
     *
     * @param name The name of the server (defaults to the `Implementation-Title` of the application)
     * @param version The version of the server (defaults to the `Implementation-Version` of the application)
     * @param title A human-readable name of the server
     */
    public fun server(name: String, version: String, title: String? = null): ToolsHandlerBuilder {
        identity = ServerIdentity(name, version, title)
        return this
    }

    /**
     * Explains to the model how to use the tools of this server, e.g. which tool to call first or how tools
     * relate to each other.
     *
     * Clients may shorten long instructions, so keep them focused.
     *
     * @param instructions The instructions to be passed to the model
     */
    public fun instructions(instructions: String): ToolsHandlerBuilder {
        this.instructions = instructions
        return this
    }

    /**
     * Allows a web page served from the given origin to use this endpoint from within a browser.
     *
     * Requests from browsers carry an origin and are rejected by default to prevent malicious web pages from
     * accessing the endpoint (e.g. via DNS rebinding). Agents do not send an origin and are not affected.
     *
     * @param origin The origin to allow, such as "https://example.com"
     */
    public fun allowOrigin(origin: String): ToolsHandlerBuilder {
        origins.add(origin)
        return this
    }

    /**
     * Sets how long clients may cache the list of tools (defaults to 5 minutes).
     *
     * @param duration The duration clients may consider the tool list fresh
     * @throws IllegalArgumentException Thrown if the given duration is negative
     */
    public fun cacheDuration(duration: Duration): ToolsHandlerBuilder {
        require(!duration.isNegative()) { "The cache duration must not be negative" }

        cacheDuration = duration
        return this
    }

    /**
     * Sets the mapper used to read the arguments and write the results of tools, and to describe their types to
     * the client.
     *
     * @param mapper The mapper to be used
     */
    public fun serialization(mapper: JsonMapper): ToolsHandlerBuilder {
        serialization = mapper
        return this
    }

    /**
     * Creates the endpoint.
     *
     * @throws IllegalStateException Thrown if a tool cannot be described to clients
     * @throws IllegalArgumentException Thrown if an allowed origin is not valid
     */
    override fun build(): Handler {
        val mapper = serialization?.let { McpSerialization.forRuntime(it) } ?: McpSerialization.Default

        val names = HashSet<String>()

        for (definition in tools) {
            if (!names.add(definition.name)) {
                throw IllegalStateException("There is more than one tool named '${definition.name}'")
            }
        }

        val instances = HashMap<KClass<*>, suspend (Request) -> Any>()

        val tools = tools.map { definition ->
            val type = definition.activatedType

            if (type != null) definition.copy(instance = getInstanceProvider(type, instances)) else definition
        }

        val registry = MethodRegistry(Serialization.default().build(), (injectors ?: Injection.default()).build(), Formatting.default().build())

        val handler = ToolsHandler(tools, registry, identity ?: ServerIdentity.default(), instructions, cacheDuration, OriginPolicy(origins), mapper)

        return Concerns.chain(concerns, handler)
    }

    internal fun addFunction(name: String, description: String, function: Function<*>): ToolsHandlerBuilder {
        tools.add(MethodToolDefinition.fromDelegate(name, description, function))
        return this
    }

    private fun getInstanceProvider(type: KClass<*>, instances: MutableMap<KClass<*>, suspend (Request) -> Any>): suspend (Request) -> Any {
        instances[type]?.let { return it }

        val custom = instanceProvider

        val provider: suspend (Request) -> Any

        if (custom != null) {
            provider = { request -> custom(type, request) }
        } else {
            val instance = createInstance(type)

            provider = { _ -> instance }
        }

        instances[type] = provider

        return provider
    }

    private fun createInstance(type: KClass<*>): Any {
        type.objectInstance?.let { return it }

        val constructor = type.constructors.firstOrNull { constructor -> constructor.parameters.all(KParameter::isOptional) }
            ?: throw IllegalStateException(
                "Type '${type.qualifiedName ?: type.java.name}' needs a public parameterless constructor to provide tools. Use dependency injection to create instances with dependencies."
            )

        constructor.isAccessible = true

        return constructor.callBy(emptyMap())
    }

    // endregion

}
