package org.genhttp.modules.reflection.operations

import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseBuilder
import org.genhttp.modules.reflection.FromBody
import org.genhttp.modules.reflection.MethodRegistry
import org.genhttp.modules.reflection.ResultWrapper
import org.genhttp.modules.reflection.canFormat

import java.io.InputStream
import java.nio.ByteBuffer

import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.isSubclassOf

/**
 * Analyzes the signature of service methods.
 */
public object SignatureAnalyzer {

    /**
     * Determines the source of every argument of the given function.
     *
     * @param pathArguments The names of the arguments provided by the route (case-insensitive)
     */
    public fun getArguments(server: Server, method: KFunction<*>, pathArguments: Set<String>, registry: MethodRegistry): Map<String, OperationArgument> {
        val result = sortedMapOf<String, OperationArgument>(String.CASE_INSENSITIVE_ORDER)

        val ordered = LinkedHashMap<String, OperationArgument>()

        for (param in valueParameters(method)) {
            val name = param.name ?: continue

            val argument = when {
                pathArguments.any { it.equals(name, ignoreCase = true) } -> create(name, param, OperationArgumentSource.Path)
                canInject(server, param, registry) -> create(name, param, OperationArgumentSource.Injected)
                param.type.classifier == InputStream::class -> create(name, param, OperationArgumentSource.Streamed)
                param.canFormat(registry.formatting) -> {
                    if (param.findAnnotation<FromBody>() != null) {
                        create(name, param, OperationArgumentSource.Body)
                    } else {
                        create(name, param, OperationArgumentSource.Query)
                    }
                }
                else -> create(name, param, OperationArgumentSource.Content)
            }

            if (result.containsKey(name)) {
                throw IllegalArgumentException("Parameter '$name' of '${method.name}' is declared multiple times (names are case-insensitive)")
            }

            result[name] = argument
            ordered[name] = argument
        }

        return CaseInsensitiveMap(ordered)
    }

    /**
     * Returns the value parameters of the given function (without instance or extension receivers).
     */
    public fun valueParameters(method: KFunction<*>): List<KParameter> = method.parameters.filter { it.kind == KParameter.Kind.VALUE }

    private fun create(name: String, param: KParameter, source: OperationArgumentSource) =
        OperationArgument(name, param.type, source, param.isOptional)

    private fun canInject(server: Server, param: KParameter, registry: MethodRegistry): Boolean {
        val type = param.type.classifier as? KClass<*> ?: return false
        return registry.injection.any { it.supports(server, type) }
    }

    /**
     * Determines how the result of the given function is sent to the client.
     */
    public fun getResult(method: KFunction<*>, registry: MethodRegistry): OperationResult {
        val type = findActualType(method.returnType)
        val classifier = type.classifier as? KClass<*>

        if (classifier == null || classifier == Unit::class || classifier == Nothing::class || classifier == Void::class) {
            return OperationResult(method.returnType, OperationResultSink.None)
        }

        if (classifier.isSubclassOf(Handler::class) || classifier.isSubclassOf(HandlerBuilder::class) ||
            classifier.isSubclassOf(Response::class) || classifier.isSubclassOf(ResponseBuilder::class)
        ) {
            return OperationResult(type, OperationResultSink.Dynamic)
        }

        if (classifier.isSubclassOf(InputStream::class) || classifier == ByteArray::class || classifier.isSubclassOf(ByteBuffer::class)) {
            return OperationResult(type, OperationResultSink.Binary)
        }

        if (registry.formatting.canHandle(classifier)) {
            return OperationResult(type, OperationResultSink.Formatter)
        }

        return OperationResult(type, OperationResultSink.Serializer)
    }

    private fun findActualType(type: KType): KType {
        val classifier = type.classifier as? KClass<*> ?: return type

        if (classifier.isSubclassOf(ResultWrapper::class)) {
            return type.arguments.firstOrNull()?.type ?: type
        }

        return type
    }

    /**
     * Read-only map with case-insensitive keys that preserves the order of insertion.
     */
    private class CaseInsensitiveMap(private val ordered: LinkedHashMap<String, OperationArgument>) : Map<String, OperationArgument> by ordered {

        private val lookup = sortedMapOf<String, OperationArgument>(String.CASE_INSENSITIVE_ORDER).apply { putAll(ordered) }

        override fun get(key: String): OperationArgument? = lookup[key]

        override fun containsKey(key: String): Boolean = lookup.containsKey(key)

    }

}
