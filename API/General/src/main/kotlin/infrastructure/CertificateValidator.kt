package org.genhttp.api.infrastructure

import java.security.cert.X509Certificate

/**
 * When passed to the binding method of the server host, implementations of this interface will be called to
 * negotiate client authentication during the TLS handshake.
 */
public interface CertificateValidator {

    /**
     * If true, the client is expected to send a certificate for authentication. If no certificates are sent,
     * the handshake will fail and no connection can be established.
     */
    public val requireCertificate: Boolean
        get() = true

    /**
     * Specifies how the server should check whether the client certificate has been revoked.
     */
    public val revocationCheck: X509RevocationMode
        get() = X509RevocationMode.Offline

    /**
     * Will be called for each certificate presented by the client to validate, whether this certificate should
     * be used for the connection.
     *
     * @param certificate The certificate presented by the client
     * @param chain The chain of certificate authorities associated with the remote certificate
     * @param policyErrors The errors associated with the remote certificate (empty, if the certificate is valid)
     * @return true, if the certificate should be accepted
     */
    public fun validate(certificate: X509Certificate?, chain: List<X509Certificate>?, policyErrors: Set<SslPolicyError>): Boolean

}
