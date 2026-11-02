package org.genhttp.modules.mcp.provider

import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseBuilder
import org.genhttp.modules.conversion.toJavaType
import org.genhttp.modules.mcp.schema.ArgumentValidation
import org.genhttp.modules.mcp.schema.SchemaGenerator
import org.genhttp.modules.mcp.schema.ToolArgument
import org.genhttp.modules.reflection.ExecutionMode
import org.genhttp.modules.reflection.ExecutionSettings
import org.genhttp.modules.reflection.MethodConfiguration
import org.genhttp.modules.reflection.MethodRegistry
import org.genhttp.modules.reflection.ResultWrapper
import org.genhttp.modules.reflection.operations.OperationArgument
import org.genhttp.modules.reflection.operations.OperationArgumentSource
import org.genhttp.modules.reflection.operations.OperationBuilder
import org.genhttp.modules.reflection.operations.SignatureAnalyzer

import tools.jackson.databind.json.JsonMapper

import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer

import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.isSubclassOf

/**
 * Analyzes a tool function using the reflection framework, so that injection and interceptors work the same
 * way as for webservices.
 */
internal object MethodToolFactory {

    private val Settings = ExecutionSettings(ExecutionMode.Reflection)

    private val Configuration = ToolConfiguration()

    fun create(server: Server, definition: MethodToolDefinition, registry: MethodRegistry, mapper: JsonMapper): RegisteredTool {
        val method = definition.method

        val operation = OperationBuilder.create(server, null, method, definition.delegate, Settings, Configuration, registry)

        val parameters = SignatureAnalyzer.valueParameters(method)

        val bindings = ArrayList<ToolParameter>(parameters.size)
        val arguments = ArrayList<ToolArgument>(parameters.size)

        for ((index, parameter) in parameters.withIndex()) {
            val name = parameter.name

            val argument = name?.let { operation.arguments[it] }
                ?: throw IllegalStateException("Unable to analyze parameter $index of tool '${definition.name}'.")

            when (argument.source) {
                OperationArgumentSource.Injected -> bindings.add(ToolParameter(argument, null))

                OperationArgumentSource.Streamed ->
                    throw IllegalStateException("Parameter '$name' of tool '${definition.name}' is a stream, which cannot be sent by a client.")

                else -> {
                    val required = !parameter.isOptional && !parameter.type.isMarkedNullable

                    val toolArgument = ToolArgument(
                        name,
                        parameter,
                        required,
                        mapper.constructType(parameter.type.toJavaType()),
                        ArgumentValidation.forParameter(parameter, mapper)
                    )

                    bindings.add(ToolParameter(argument, toolArgument))
                    arguments.add(toolArgument)
                }
            }
        }

        val outputType = getOutputType(definition, method.returnType)

        val inputSchema = SchemaGenerator.arguments(arguments, mapper)

        val outputSchema = outputType?.let { SchemaGenerator.output(it, mapper) }

        val invoker = MethodToolInvoker(definition.instance, operation, registry, bindings, mapper, outputType == null)

        val description = ToolDocumentation.describe(definition.description, method, operation)

        return RegisteredTool(definition.name, definition.title, description, definition.hints, inputSchema, outputSchema, invoker)
    }

    private fun getOutputType(definition: MethodToolDefinition, returnType: KType): KType? {
        var type = returnType

        var classifier = type.classifier as? KClass<*> ?: return type

        if (classifier == Unit::class || classifier == Nothing::class || classifier == Void::class) {
            return null
        }

        if (classifier.isSubclassOf(ResultWrapper::class) && type.arguments.isNotEmpty()) {
            type = type.arguments[0].type ?: return type
            classifier = type.classifier as? KClass<*> ?: return type
        }

        if (classifier.isSubclassOf(Handler::class) || classifier.isSubclassOf(HandlerBuilder::class) || classifier.isSubclassOf(Response::class) ||
            classifier.isSubclassOf(ResponseBuilder::class) || classifier.isSubclassOf(InputStream::class) || classifier.isSubclassOf(OutputStream::class) ||
            classifier == ByteArray::class || classifier.isSubclassOf(ByteBuffer::class)
        ) {
            throw IllegalStateException("Tool '${definition.name}' returns '$type', which cannot be sent to a client. Please return a string, a ToolResult or a serializable object.")
        }

        return type
    }

    private class ToolConfiguration : MethodConfiguration {

        override val supportedMethods: Set<RequestMethod> = setOf(RequestMethod.Post)

    }

}

/**
 * A parameter of a tool function, either read from the arguments sent by the client or injected by the
 * framework (if there is no tool argument).
 */
internal class ToolParameter(val operation: OperationArgument, val argument: ToolArgument?)
