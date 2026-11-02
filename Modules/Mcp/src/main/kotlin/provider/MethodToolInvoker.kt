package org.genhttp.modules.mcp.provider

import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.Request
import org.genhttp.modules.mcp.schema.ArgumentValidation
import org.genhttp.modules.mcp.schema.ToolArgument
import org.genhttp.modules.reflection.MethodRegistry
import org.genhttp.modules.reflection.ResultWrapper
import org.genhttp.modules.reflection.operations.ArgumentProvider
import org.genhttp.modules.reflection.operations.Operation
import org.genhttp.modules.reflection.operations.SignatureAnalyzer

import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

import java.lang.reflect.InvocationTargetException

import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.KParameter
import kotlin.reflect.full.callSuspendBy
import kotlin.reflect.jvm.isAccessible

/**
 * Invokes a tool function: reads its arguments, injects framework values, runs the interceptors and captures
 * the result.
 */
internal class MethodToolInvoker(
    private val instance: (suspend (Request) -> Any)?,
    private val operation: Operation,
    private val registry: MethodRegistry,
    private val parameters: List<ToolParameter>,
    private val mapper: JsonMapper,
    private val isVoid: Boolean
) {

    private val function = operation.method

    private val delegate = operation.delegate

    private val valueParameters: List<KParameter> = SignatureAnalyzer.valueParameters(function)

    private val instanceParameter: KParameter? = function.parameters.firstOrNull { it.kind == KParameter.Kind.INSTANCE }

    init {
        if (delegate == null) {
            runCatching { function.isAccessible = true }
        }
    }

    // region Functionality

    suspend fun invoke(arguments: JsonNode, context: ToolContext): ToolOutcome {
        val values = arrayOfNulls<Any?>(parameters.size)
        val omitted = BooleanArray(parameters.size)

        val named = if (operation.interceptors.isNotEmpty()) LinkedHashMap<ByteString, Any?>(parameters.size) else null

        var invalid: MutableList<String>? = null

        try {
            for (i in parameters.indices) {
                val parameter = parameters[i]

                val argument = parameter.argument

                val value: Any?

                if (argument != null) {
                    when (val read = read(arguments, argument)) {
                        is ReadResult.Problem -> return ToolOutcome.failed(read.message)

                        is ReadResult.Missing -> {
                            value = null
                            omitted[i] = argument.parameter.isOptional
                        }

                        is ReadResult.Value -> value = read.value
                    }

                    // an omitted value is validated as null, the declared default value cannot be read
                    argument.validator?.validate(value, argument.name, invalid ?: mutableListOf<String>().also { invalid = it })
                } else {
                    value = ArgumentProvider.getInjectedArgument(context.request, context.handler, parameter.operation, registry)
                }

                values[i] = value

                named?.put(parameter.operation.name, value)
            }

            val violations = invalid

            if (!violations.isNullOrEmpty()) {
                return ToolOutcome.failed(ArgumentValidation.format(violations))
            }

            if (named != null) {
                for (interceptor in operation.interceptors) {
                    val interception = interceptor.intercept(context.request, operation, named)

                    if (interception != null) {
                        return ToolOutputs.capture(interception.payload, mapper)
                    }
                }
            }

            var output = call(context.request, values, omitted)

            if (output is ResultWrapper) {
                output = output.payload
            }

            return if (isVoid) ToolOutputs.empty() else ToolOutputs.capture(output, mapper)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ToolOutputs.fail(e)
        }
    }

    private suspend fun call(request: Request, values: Array<Any?>, omitted: BooleanArray): Any? {
        try {
            if (delegate != null) {
                return SuspendLambdas.invoke(delegate, values)
            }

            val map = HashMap<KParameter, Any?>(valueParameters.size + 1)

            if (instanceParameter != null) {
                val provider = instance ?: throw IllegalStateException("No instance has been provided to invoke '${function.name}' on")

                map[instanceParameter] = provider(request)
            }

            for (i in valueParameters.indices) {
                if (!omitted[i]) {
                    map[valueParameters[i]] = values[i]
                }
            }

            return function.callSuspendBy(map)
        } catch (e: InvocationTargetException) {
            throw e.targetException ?: e
        }
    }

    private fun read(arguments: JsonNode, argument: ToolArgument): ReadResult {
        val element = arguments.get(argument.name)

        if (element == null || element.isNull) {
            return if (argument.required) ReadResult.Problem("The argument '${argument.name}' is required.") else ReadResult.Missing
        }

        return try {
            ReadResult.Value(mapper.treeToValue<Any?>(element, argument.javaType))
        } catch (e: JacksonException) {
            ReadResult.Problem(ArgumentValidation.format(listOf("${getPath(argument.name, e)}: The argument '${argument.name}' does not match its schema: ${e.originalMessage}")))
        }
    }

    /**
     * The location of the value that could not be read, e.g. "skills[0].level".
     */
    private fun getPath(name: String, exception: JacksonException): String {
        val path = StringBuilder(name)

        for (reference in exception.path) {
            val property = reference.propertyName

            if (property != null) {
                path.append('.').append(property)
            } else if (reference.index >= 0) {
                path.append('[').append(reference.index).append(']')
            }
        }

        return path.toString()
    }

    // endregion

    private sealed interface ReadResult {

        class Value(val value: Any?) : ReadResult

        object Missing : ReadResult

        class Problem(val message: String) : ReadResult

    }

}

/**
 * Invokes suspend lambdas with a dynamic number of arguments.
 */
internal object SuspendLambdas {

    @Suppress("UNCHECKED_CAST")
    suspend fun invoke(function: Function<*>, a: Array<Any?>): Any? =
        when (a.size) {
            0 -> (function as suspend () -> Any?)()
            1 -> (function as suspend (Any?) -> Any?)(a[0])
            2 -> (function as suspend (Any?, Any?) -> Any?)(a[0], a[1])
            3 -> (function as suspend (Any?, Any?, Any?) -> Any?)(a[0], a[1], a[2])
            4 -> (function as suspend (Any?, Any?, Any?, Any?) -> Any?)(a[0], a[1], a[2], a[3])
            5 -> (function as suspend (Any?, Any?, Any?, Any?, Any?) -> Any?)(a[0], a[1], a[2], a[3], a[4])
            6 -> (function as suspend (Any?, Any?, Any?, Any?, Any?, Any?) -> Any?)(a[0], a[1], a[2], a[3], a[4], a[5])
            7 -> (function as suspend (Any?, Any?, Any?, Any?, Any?, Any?, Any?) -> Any?)(a[0], a[1], a[2], a[3], a[4], a[5], a[6])
            8 -> (function as suspend (Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?) -> Any?)(a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7])
            9 -> (function as suspend (Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?) -> Any?)(a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8])
            10 -> (function as suspend (Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?) -> Any?)(a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9])
            11 -> (function as suspend (Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?) -> Any?)(a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10])
            12 -> (function as suspend (Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?) -> Any?)(a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11])
            13 -> (function as suspend (Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?) -> Any?)(a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11], a[12])
            14 -> (function as suspend (Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?) -> Any?)(a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11], a[12], a[13])
            15 -> (function as suspend (Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?) -> Any?)(a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11], a[12], a[13], a[14])
            16 -> (function as suspend (Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?, Any?) -> Any?)(a[0], a[1], a[2], a[3], a[4], a[5], a[6], a[7], a[8], a[9], a[10], a[11], a[12], a[13], a[14], a[15])
            else -> throw IllegalArgumentException("Lambdas with more than 16 parameters are not supported, use a function reference instead")
        }

}
