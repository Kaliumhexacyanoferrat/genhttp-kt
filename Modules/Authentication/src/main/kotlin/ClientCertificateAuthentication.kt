package org.genhttp.modules.authentication

import org.genhttp.modules.authentication.clientcertificate.ClientCertificateAuthenticationBuilder

/**
 * Allows to grant access based on the certificate presented by the client.
 */
public object ClientCertificateAuthentication {

    /**
     * Creates a concern that checks access permission based on the certificate presented by the client during
     * the SSL handshake.
     *
     * @return The newly created concern
     */
    public fun create(): ClientCertificateAuthenticationBuilder = ClientCertificateAuthenticationBuilder()

}
