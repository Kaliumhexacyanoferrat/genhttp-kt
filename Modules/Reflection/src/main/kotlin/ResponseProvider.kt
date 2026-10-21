package org.genhttp.modules.reflection

import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.content.ProviderException
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseBuilder
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.io.streaming.ByteArrayContent
import org.genhttp.modules.io.streaming.MemoryContent
import org.genhttp.modules.io.streaming.StreamContent
import org.genhttp.modules.reflection.operations.Operation
import org.genhttp.modules.reflection.operations.OperationResultSink

import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Converts the results of operations into responses.
 *
 * @param registry The registries used to format or serialize results
 */
public class ResponseProvider(private val registry: MethodRegistry) {

    /**
     * Creates the response for the given result of an operation.
     *
     * @param request The request that has been handled
     * @param operation The operation that has been executed
     * @param result The result returned by the operation
     * @param acceptedFormat The format accepted by the client (value of the "Accept" header)
     * @param adjustments Modifications to be applied to the response
     */
    public suspend fun getResponse(
        request: Request,
        operation: Operation,
        result: Any?,
        acceptedFormat: ByteString?,
        adjustments: (ResponseBuilder.() -> Unit)? = null
    ): Response? {
        // no result = 204
        if (result == null) {
            return getNoContent(request, adjustments)
        }

        // unwrap the result if applicable
        if (result is ResultWrapper) {
            return getResponse(request, operation, result.payload, acceptedFormat) { result.apply(this) }
        }

        return when (operation.result.sink) {
            OperationResultSink.Dynamic -> getDynamicResponse(request, result, adjustments)
            OperationResultSink.Binary -> getBinaryResponse(request, result, adjustments)
            OperationResultSink.Formatter -> getFormattedResponse(request, result, adjustments)
            OperationResultSink.Serializer -> getSerializedResponse(request, result, acceptedFormat, adjustments)
            OperationResultSink.None -> getNoContent(request, adjustments)
        }
    }

    private fun getNoContent(request: Request, adjustments: (ResponseBuilder.() -> Unit)?): Response =
        request.respond()
            .status(ResponseStatus.NoContent)
            .adjust(adjustments)
            .build()

    private suspend fun getDynamicResponse(request: Request, result: Any, adjustments: (ResponseBuilder.() -> Unit)?): Response? = when (result) {
        is ResponseBuilder -> result.adjust(adjustments).build()
        is Response -> result
        is HandlerBuilder -> result.build().handle(request)
        is Handler -> result.handle(request)
        else -> throw ProviderException(ResponseStatus.InternalServerError, "Unexpected return type '${result::class}' to be processed by dynamic sink")
    }

    private fun getBinaryResponse(request: Request, data: Any, adjustments: (ResponseBuilder.() -> Unit)?): Response {
        val response = request.respond().adjust(adjustments)

        when (data) {
            is InputStream -> response.content(StreamContent(data, ContentType.ApplicationForceDownload, null, null, null))
            is ByteArray -> response.content(ByteArrayContent(data, ContentType.ApplicationForceDownload, null))
            is ByteBuffer -> response.content(MemoryContent(data, ContentType.ApplicationForceDownload, null))
            else -> throw ProviderException(ResponseStatus.InternalServerError, "Unexpected type '${data::class}' for stream sink")
        }

        return response.build()
    }

    private fun getFormattedResponse(request: Request, result: Any, adjustments: (ResponseBuilder.() -> Unit)?): Response =
        request.respond()
            .content(registry.formatting.getContent(result, result::class))
            .adjust(adjustments)
            .build()

    private suspend fun getSerializedResponse(request: Request, result: Any, acceptedFormat: ByteString?, adjustments: (ResponseBuilder.() -> Unit)?): Response {
        val serializer = registry.serialization.getSerialization(request, acceptedFormat)
            ?: throw ProviderException(ResponseStatus.UnsupportedMediaType, "Requested format is not supported")

        return serializer.serialize(request, result)
            .adjust(adjustments)
            .build()
    }

}
