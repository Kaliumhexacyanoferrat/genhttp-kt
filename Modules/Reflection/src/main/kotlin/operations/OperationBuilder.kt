package org.genhttp.modules.reflection.operations

import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.Server
import org.genhttp.modules.reflection.ExecutionSettings
import org.genhttp.modules.reflection.MethodConfiguration
import org.genhttp.modules.reflection.MethodRegistry
import org.genhttp.modules.reflection.routing.OperationRoute
import org.genhttp.modules.reflection.routing.RoutingSegment
import org.genhttp.modules.reflection.routing.segments.ClosingSegment
import org.genhttp.modules.reflection.routing.segments.RegexSegment
import org.genhttp.modules.reflection.routing.segments.SimpleVariableSegment
import org.genhttp.modules.reflection.routing.segments.StringSegment

import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf

/**
 * Analyzes service methods and creates the operations to be executed by the frameworks.
 */
public object OperationBuilder {

    private val IndexRoute = OperationRoute("/", listOf(ClosingSegment(forceTrailingSlash = false, wildcard = false)), false)

    private val WildcardIndexRoute = OperationRoute("/", listOf(ClosingSegment(forceTrailingSlash = false, wildcard = true)), true)

    /**
     * Creates an operation for the given function.
     *
     * @param server The server the operation will be executed by
     * @param definition The path of the operation (e.g. "/users/:id"), if any
     * @param method The function to be executed (or describing the signature of the delegate)
     * @param delegate The lambda to be executed, if the operation is not backed by a function
     * @param executionSettings The settings used to execute the function
     * @param configuration The configuration of the operation
     * @param registry The registries used to execute the operation
     * @param forceTrailingSlash Whether the route requires a trailing slash
     */
    public fun create(
        server: Server,
        definition: String?,
        method: KFunction<*>,
        delegate: Function<*>?,
        executionSettings: ExecutionSettings,
        configuration: MethodConfiguration,
        registry: MethodRegistry,
        forceTrailingSlash: Boolean = false
    ): Operation {
        val isWildcard = checkWildcardRoute(method.returnType)

        val route = if (definition.isNullOrBlank()) {
            if (isWildcard) WildcardIndexRoute else IndexRoute
        } else {
            val normalized = normalize(definition)
            val parts = normalized.split('/').filter { it.isNotEmpty() }

            val segments = ArrayList<RoutingSegment>(parts.size + 1)

            for (part in parts) {
                val colonCount = part.count { it == ':' }

                if (part.startsWith(':') && isValidVariable(part.substring(1)) && colonCount == 1) {
                    segments.add(SimpleVariableSegment(part.substring(1)))
                } else if (part.contains("?<") || colonCount > 0) {
                    segments.add(RegexSegment(part))
                } else {
                    segments.add(StringSegment(part))
                }
            }

            segments.add(ClosingSegment(forceTrailingSlash || definition.endsWith('/'), isWildcard))

            OperationRoute(getDisplayName(definition, forceTrailingSlash), segments, isWildcard)
        }

        val pathArguments = route.segments.flatMap { it.providedArguments.asList() }.toSet()

        val arguments = SignatureAnalyzer.getArguments(server, method, pathArguments, registry)
        val result = SignatureAnalyzer.getResult(method, registry)
        val interceptors = InterceptorAnalyzer.getInterceptors(method)

        return Operation(method, delegate, executionSettings, configuration, route, result, arguments, interceptors)
    }

    private fun checkWildcardRoute(returnType: KType): Boolean {
        val classifier = returnType.classifier as? KClass<*> ?: return false
        return classifier.isSubclassOf(HandlerBuilder::class) || classifier.isSubclassOf(Handler::class)
    }

    private fun getDisplayName(definition: String, forceTrailingSlash: Boolean): String {
        var name = normalize(definition)

        name = replaceMatches(name, definition, Regex(":([A-Za-z0-9]+)")) // :var
        name = replaceMatches(name, definition, Regex("\\(\\?<([a-z]+)>([^)]+)\\)")) // (?<ean13>[0-9]{12,13})

        if (forceTrailingSlash || definition.endsWith('/')) {
            name += "/"
        }

        if (name.length > 1) {
            name = "/$name"
        }

        return name
    }

    private fun replaceMatches(current: String, definition: String, pattern: Regex): String {
        var result = current

        for (match in pattern.findAll(definition)) {
            result = result.replace(match.value, "{${match.groupValues[1]}}")
        }

        return result
    }

    private fun normalize(definition: String): String {
        var trimStart = 0
        var trimEnd = 0

        if (definition.isNotEmpty()) {
            if (definition[0] == '/') trimStart = 1
            if (definition[definition.length - 1] == '/') trimEnd = 1
        }

        if (trimStart + trimEnd > definition.length) {
            return ""
        }

        return definition.substring(trimStart, definition.length - trimEnd)
    }

    private fun isValidVariable(value: String): Boolean = value.all { it in 'A'..'Z' || it in 'a'..'z' || it in '0'..'9' }

}
