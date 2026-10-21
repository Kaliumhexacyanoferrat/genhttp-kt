package org.genhttp.modules.controllers.provider

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.modules.controllers.ControllerAction
import org.genhttp.modules.controllers.ControllerActionConfiguration
import org.genhttp.modules.controllers.FromPath
import org.genhttp.modules.reflection.ExecutionSettings
import org.genhttp.modules.reflection.MethodAttribute
import org.genhttp.modules.reflection.MethodCollection
import org.genhttp.modules.reflection.MethodConfiguration
import org.genhttp.modules.reflection.MethodHandler
import org.genhttp.modules.reflection.MethodRegistry
import org.genhttp.modules.reflection.ServiceMethodProvider
import org.genhttp.modules.reflection.operations.Operation
import org.genhttp.modules.reflection.operations.OperationBuilder

import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation

/**
 * Routes requests to the actions of a controller, deriving the paths from the names of the public functions
 * (`index()` for the root, `fun listUsers()` for `/list-users/`).
 */
public class ControllerHandler(
    private val type: KClass<*>,
    private val instanceProvider: suspend (Request) -> Any,
    private val executionSettings: ExecutionSettings,
    private val registry: MethodRegistry
) : Handler, ServiceMethodProvider {

    private var _methods: MethodCollection? = null

    override val methods: MethodCollection
        get() = _methods ?: throw IllegalStateException("Handler is not prepared yet")

    override suspend fun prepare(server: Server) {
        val found = mutableListOf<MethodHandler>()

        for (method in type.declaredMemberFunctions) {
            if (method.visibility != KVisibility.PUBLIC) {
                continue
            }

            val annotation = method.findAnnotation<ControllerAction>()

            val configuration: MethodConfiguration = if (annotation != null) ControllerActionConfiguration(annotation) else MethodAttribute()

            val arguments = findPathArguments(method)

            val operation = createOperation(server, method, executionSettings, configuration, arguments, registry)

            found.add(MethodHandler(operation, instanceProvider, registry))
        }

        val result = MethodCollection(found)

        result.prepare(server)

        _methods = result
    }

    override suspend fun handle(request: Request): Response? = methods.handle(request)

    private companion object {

        val HyphenMatcher = Regex("([a-z])([A-Z0-9]+)")

        fun createOperation(
            server: Server,
            method: KFunction<*>,
            executionSettings: ExecutionSettings,
            configuration: MethodConfiguration,
            arguments: List<String>,
            registry: MethodRegistry
        ): Operation {
            val pathArguments = arguments.joinToString("/") { ":$it" }

            if (method.name.equals("index", ignoreCase = true)) {
                val path = if (pathArguments.isNotEmpty()) "/$pathArguments/" else null
                return OperationBuilder.create(server, path, method, null, executionSettings, configuration, registry, true)
            }

            val path = "/${hyphenCase(method.name)}"

            return OperationBuilder.create(
                server,
                if (pathArguments.isNotEmpty()) "$path/$pathArguments/" else "$path/",
                method,
                null,
                executionSettings,
                configuration,
                registry,
                true
            )
        }

        fun findPathArguments(method: KFunction<*>): List<String> =
            method.parameters
                .filter { it.kind == KParameter.Kind.VALUE && it.findAnnotation<FromPath>() != null }
                .mapNotNull { it.name }

        fun hyphenCase(input: String): String = HyphenMatcher.replace(input, "$1-$2").lowercase()

    }

}
