package org.genhttp.modules.authentication.clientcertificate

import org.genhttp.api.content.Concern
import org.genhttp.api.content.Handler
import org.genhttp.api.content.ProviderException
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.authentication.setUser

/**
 * Checks the certificate presented by the client before the request is passed to the wrapped content.
 */
public class ClientCertificateAuthenticationConcern internal constructor(
    override val content: Handler,
    private val options: ClientCertificateAuthenticationOptions
) : Concern {

    // region Functionality

    override suspend fun prepare(server: Server) {
        content.prepare(server)
    }

    override suspend fun handle(request: Request): Response? {
        val certificate = request.client.certificate

        val authorizer = options.authorizer

        if (authorizer != null) {
            if (!authorizer(request, certificate)) {
                throw ProviderException(ResponseStatus.Forbidden, "You are not allowed to access this resource")
            }
        }

        val userMapping = options.userMapping

        if (userMapping != null) {
            val user = userMapping(request, certificate)

            if (user != null) {
                request.setUser(user)
            }
        }

        return content.handle(request)
    }

    // endregion

}
