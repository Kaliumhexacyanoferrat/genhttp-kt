package org.genhttp.modules.authentication.multi

import org.genhttp.api.content.Concern
import org.genhttp.api.content.Handler
import org.genhttp.api.content.ProviderException
import org.genhttp.api.content.documentation.ApiRequestHeader
import org.genhttp.api.content.documentation.ApiResponse
import org.genhttp.api.content.documentation.ApiResponseHeader
import org.genhttp.api.content.documentation.DocumentedConcern
import org.genhttp.api.content.documentation.OperationDocumentation
import org.genhttp.api.content.documentation.security.SecurityScheme
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus

/**
 * Passes the request through a list of authentication concerns, until one of them does not respond with
 * a `401 Unauthorized`.
 *
 * @param content The handler to be wrapped by this concern
 * @param delegatingConcerns The concerns used to authenticate the request
 */
public class MultiAuthenticationConcern(
    override val content: Handler,
    private val delegatingConcerns: List<Concern>
) : DocumentedConcern {

    // region Functionality

    override suspend fun handle(request: Request): Response? {
        var lastResponse: ResponseOrException? = null

        for (concern in delegatingConcerns) {
            lastResponse = try {
                ResponseOrException(concern.handle(request))
            } catch (e: ProviderException) {
                ResponseOrException(exception = e)
            }

            if (lastResponse.status != ResponseStatus.Unauthorized) {
                return lastResponse.get()
            }
        }

        return lastResponse?.get()
            ?: request.respond()
                .status(ResponseStatus.Unauthorized)
                .build()
    }

    override suspend fun prepare(server: Server) {
        content.prepare(server)
    }

    override fun addDocumentation(operation: OperationDocumentation) {
        val schemes = mutableListOf<SecurityScheme>()

        val entries = mutableListOf<Annotation>()

        for (concern in delegatingConcerns.filterIsInstance<DocumentedConcern>()) {
            val child = OperationDocumentation(operation.method)

            concern.addDocumentation(child)

            schemes.addAll(child.authentication.flatMap { it.anyOf })
            entries.addAll(child.entries)
        }

        if (schemes.isNotEmpty()) {
            operation.requireAuthentication(*schemes.distinct().toTypedArray())
        }

        operation.response(ResponseStatus.Unauthorized, "The request could not be authenticated with any of the supported methods")

        for (entry in entries) {
            when (entry) {
                is ApiResponse -> operation.response(entry.status, entry.description.ifEmpty { null }, entry.type.takeIf { it != Nothing::class }, entry.contentType.ifEmpty { null })
                is ApiResponseHeader -> operation.responseHeader(entry.name, entry.description.ifEmpty { null }, entry.status.firstOrNull(), entry.type.takeIf { it != Nothing::class })
                is ApiRequestHeader -> operation.requestHeader(entry.name, entry.description.ifEmpty { null }, false, entry.type.takeIf { it != Nothing::class })
            }
        }
    }

    // endregion

    // region Helper structure

    private class ResponseOrException(val response: Response? = null, val exception: ProviderException? = null) {

        fun get(): Response? {
            if (exception != null) {
                throw exception
            }

            return response
        }

        val status: ResponseStatus?
            get() = exception?.status ?: response?.status

    }

    // endregion

}
