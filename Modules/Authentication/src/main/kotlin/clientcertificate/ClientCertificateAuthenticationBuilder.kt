package org.genhttp.modules.authentication.clientcertificate

import org.genhttp.api.content.Concern
import org.genhttp.api.content.Handler
import org.genhttp.api.content.authentication.User
import org.genhttp.api.content.documentation.security.SecurityScheme
import org.genhttp.api.protocol.Request
import org.genhttp.modules.authentication.AuthenticationBuilder
import org.genhttp.modules.authentication.documentation.SchemeDocumentation

import java.security.cert.X509Certificate

/**
 * Configures a concern that grants access based on the certificate presented by the client during the TLS
 * handshake.
 *
 * Client certificates are not documented as a security scheme by default, as the OpenAPI specification generated
 * by the framework (version 3.0) does not support them. To document the mechanism, pass a scheme to
 * [documentation], which is also required for a description passed to [documentation] to be shown.
 */
public class ClientCertificateAuthenticationBuilder : AuthenticationBuilder<ClientCertificateAuthenticationBuilder> {

    private val options = ClientCertificateAuthenticationOptions()

    private val schemeDocumentation = SchemeDocumentation()

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

    override fun documentation(description: String): ClientCertificateAuthenticationBuilder {
        schemeDocumentation.description = description
        return this
    }

    override fun documentation(scheme: SecurityScheme): ClientCertificateAuthenticationBuilder {
        schemeDocumentation.scheme = scheme
        return this
    }

    override fun build(content: Handler): Concern = ClientCertificateAuthenticationConcern(content, options, schemeDocumentation.resolveOptional(null))

    // endregion

}
