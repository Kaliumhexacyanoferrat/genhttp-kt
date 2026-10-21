package org.genhttp.modules.errorhandling.mappers

import com.fasterxml.jackson.annotation.JsonValue

import org.genhttp.api.content.Handler
import org.genhttp.api.content.ProviderException
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseBuilder
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.conversion.Serialization
import org.genhttp.modules.conversion.serializers.SerializationRegistry
import org.genhttp.modules.conversion.serializers.json.JsonFormat
import org.genhttp.modules.errorhandling.ErrorMapper
import org.genhttp.modules.io.apply

/**
 * Renders exceptions into structured error objects, serialized into the format
 * requested by the client (JSON by default).
 *
 * @param registry The serialization configuration to be used (defaults to JSON, XML, YAML and form support)
 */
public class StructuredErrorMapper(registry: SerializationRegistry? = null) : ErrorMapper<Exception> {

    // region Get-/Setters

    /**
     * The serialization configuration used to render the errors.
     */
    public val registry: SerializationRegistry = registry ?: getDefaultSerialization()

    // endregion

    // region Supporting data structures

    /**
     * The model describing an error that is sent to the client.
     *
     * @param status The status of the response
     * @param message The message of the exception
     * @param stackTrace The stack trace of the exception (only set in development mode)
     */
    public data class ErrorModel(val status: ResponseStatus, val message: String, val stackTrace: String? = null)

    // endregion

    // region Functionality

    override suspend fun map(request: Request, handler: Handler, error: Exception, acceptedFormat: ByteString?): Response? {
        var stackTrace: String? = null

        if (request.server.development) {
            stackTrace = error.stackTrace.joinToString("\n") { "   at $it" }.ifEmpty { null }
        }

        val message = error.message ?: error.javaClass.name

        if (error is ProviderException) {
            val model = ErrorModel(error.status, message, stackTrace)

            return render(request, model, acceptedFormat).apply(error.modifications).build()
        } else {
            val model = ErrorModel(ResponseStatus.InternalServerError, message, stackTrace)

            return render(request, model, acceptedFormat).build()
        }
    }

    override suspend fun getNotFound(request: Request, handler: Handler, acceptedFormat: ByteString?): Response? {
        val model = ErrorModel(ResponseStatus.NotFound, "The requested resource does not exist on this server")

        return render(request, model, acceptedFormat).build()
    }

    private suspend fun render(request: Request, model: ErrorModel, acceptedFormat: ByteString?): ResponseBuilder {
        val format = registry.getSerialization(request, acceptedFormat) ?: JsonFormat()

        val response = format.serialize(request, model)

        response.status(model.status)

        return response
    }

    // endregion

    private companion object {

        fun getDefaultSerialization(): SerializationRegistry {
            // the status is sent as number (such as 404) in JSON, as the enum of GenHTTP is
            val options = JsonFormat.getDefaultOptions().addMixIn(ResponseStatus::class.java, ResponseStatusMixIn::class.java)

            return Serialization.default(options.build()).build()
        }

    }

    private abstract class ResponseStatusMixIn {

        @get:JsonValue
        abstract val code: Int

    }

}
