package org.genhttp.modules.authentication.apikey

import org.genhttp.api.content.Handler
import org.genhttp.api.content.authentication.User
import org.genhttp.api.content.documentation.DocumentedConcern
import org.genhttp.api.content.documentation.OperationDocumentation
import org.genhttp.api.content.documentation.security.SecurityScheme
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.authentication.setUser

/**
 * Denies access to the wrapped content unless the client passes a valid API key.
 *
 * @param content The handler to be wrapped by this concern
 * @param keyExtractor The logic used to read the API key from a request
 * @param authenticator The logic used to check the key and to determine the user
 * @param scheme The security scheme the concern is documented with, if known
 */
public class ApiKeyConcern(
    override val content: Handler,
    private val keyExtractor: (Request) -> String?,
    private val authenticator: suspend (Request, String) -> User?,
    private val scheme: SecurityScheme? = null
) : DocumentedConcern {

    // region Functionality

    override suspend fun handle(request: Request): Response? {
        val key = keyExtractor(request)

        if (key != null) {
            val user = authenticator(request, key)

            if (user != null) {
                request.setUser(user)

                return content.handle(request)
            }

            return request.respond()
                .status(ResponseStatus.Forbidden)
                .build()
        }

        return request.respond()
            .status(ResponseStatus.Unauthorized)
            .build()
    }

    override suspend fun prepare(server: Server) {
        content.prepare(server)
    }

    override fun addDocumentation(operation: OperationDocumentation) {
        if (scheme != null) {
            operation.requireAuthentication(scheme)
        }

        operation.response(ResponseStatus.Unauthorized, "No API key has been sent with the request")
            .response(ResponseStatus.Forbidden, "The API key sent with the request is not valid")
    }

    // endregion

}
