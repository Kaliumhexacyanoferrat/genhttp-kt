package org.genhttp.modules.functional.provider

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.Response
import org.genhttp.modules.reflection.ExecutionSettings
import org.genhttp.modules.reflection.MethodCollection
import org.genhttp.modules.reflection.MethodConfiguration
import org.genhttp.modules.reflection.MethodHandler
import org.genhttp.modules.reflection.MethodRegistry
import org.genhttp.modules.reflection.ServiceMethodProvider
import org.genhttp.modules.reflection.operations.OperationBuilder

import kotlin.reflect.KFunction
import kotlin.reflect.KParameter
import kotlin.reflect.jvm.ExperimentalReflectionOnLambdas
import kotlin.reflect.jvm.reflect

/**
 * Handles requests by invoking the inline functions registered with an [InlineBuilder].
 */
public class InlineHandler(
    private val functions: List<InlineFunction>,
    private val registry: MethodRegistry,
    private val executionSettings: ExecutionSettings
) : Handler, ServiceMethodProvider {

    private var _methods: MethodCollection? = null

    override val methods: MethodCollection
        get() = _methods ?: throw IllegalStateException("Handler is not prepared yet")

    override suspend fun prepare(server: Server) {
        val found = mutableListOf<MethodHandler>()

        for (function in functions) {
            val (method, delegate) = analyze(function.delegate)

            val operation = OperationBuilder.create(server, function.path, method, delegate, executionSettings, function.configuration, registry)

            found.add(MethodHandler(operation, { function.delegate }, registry))
        }

        val result = MethodCollection(found)

        result.prepare(server)

        _methods = result
    }

    override suspend fun handle(request: Request): Response? = methods.handle(request)

    private companion object {

        @OptIn(ExperimentalReflectionOnLambdas::class)
        fun analyze(function: Function<*>): Pair<KFunction<*>, Function<*>?> {
            if (function is KFunction<*>) {
                if (function.parameters.any { it.kind == KParameter.Kind.INSTANCE }) {
                    throw IllegalArgumentException("Function '${function.name}' requires an instance, use a bound reference (instance::${function.name}) instead")
                }

                return function to null
            }

            val reflected = function.reflect()
                ?: throw IllegalArgumentException(
                    "Unable to read the signature of the given lambda. Use a suspend lambda with explicitly typed " +
                            "parameters (e.g. `get { id: Int -> ... }`) or a function reference (e.g. `get(::handler)`)."
                )

            return reflected to function
        }

    }

}
