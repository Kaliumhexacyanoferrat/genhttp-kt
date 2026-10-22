package org.genhttp.modules.authentication.multi

import org.genhttp.api.content.Concern
import org.genhttp.api.content.Handler
import org.genhttp.api.content.ProviderException
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
) : Concern {

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
