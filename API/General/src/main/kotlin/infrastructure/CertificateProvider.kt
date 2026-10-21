package org.genhttp.api.infrastructure

import org.genhttp.api.infrastructure.certificates.ObjectCertificateProvider

import java.security.KeyStore

/**
 * Allows secure endpoints to select the certificate they should authenticate the client with.
 *
 * A certificate is represented by a [KeyStore.PrivateKeyEntry] (the private key and its certificate chain),
 * which is what `X509Certificate2` represents in .NET.
 */
public fun interface CertificateProvider {

    /**
     * Select a certificate for authentication based on the given host.
     *
     * @param host The name of the host, if specified by the client (SNI)
     * @return The certificate to be used to authenticate the client
     */
    public fun provide(host: String?): KeyStore.PrivateKeyEntry?

    /**
     * Helper functions to create a certificate provider that can be used to bind a hosting endpoint.
     */
    public companion object {

        /**
         * Creates a certificate provider from the given certificate.
         *
         * @param certificate The certificate to be used to secure the endpoint
         * @return The newly created certificate provider
         */
        public fun from(certificate: KeyStore.PrivateKeyEntry): CertificateProvider = ObjectCertificateProvider(certificate)

    }

}
