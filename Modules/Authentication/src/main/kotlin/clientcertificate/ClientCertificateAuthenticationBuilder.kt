package org.genhttp.modules.authentication.clientcertificate

import org.genhttp.api.content.Concern
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.api.content.authentication.User
import org.genhttp.api.protocol.Request

import java.security.cert.X509Certificate

/**
 * Configures a concern that grants access based on the certificate presented by the client.
 */
public class ClientCertificateAuthenticationBuilder : ConcernBuilder {

    private val options = ClientCertificateAuthenticationOptions()

    // region Functionality

    /**
     * Sets a delegate that will be invoked to check whether the client is allowed to access the inner content.
     * If the delegate returns false, the client will receive a 403 forbidden response.
     *
     * @param authorizer The delegate to be invoked for authorization
     */
    public fun authorization(authorizer: suspend (Request, X509Certificate?) -> Boolean): ClientCertificateAuthenticationBuilder {
        options.authorizer = authorizer
        return this
    }

    /**
     * Sets a delegate that will be invoked to set the user record on the request, allowing inner handlers
     * to consume this information as needed.
     *
     * @param userMapping The delegate to determine the user
     */
    public fun userMapping(userMapping: suspend (Request, X509Certificate?) -> User?): ClientCertificateAuthenticationBuilder {
        options.userMapping = userMapping
        return this
    }

    override fun build(content: Handler): Concern = ClientCertificateAuthenticationConcern(content, options)

    // endregion

}
