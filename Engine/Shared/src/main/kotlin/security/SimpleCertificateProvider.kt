package org.genhttp.engine.shared.security

import org.genhttp.api.infrastructure.CertificateProvider

import java.security.KeyStore

/**
 * Provides the same certificate for every host.
 *
 * @property certificate The certificate to be provided
 */
public class SimpleCertificateProvider(public val certificate: KeyStore.PrivateKeyEntry) : CertificateProvider {

    override fun provide(host: String?): KeyStore.PrivateKeyEntry = certificate

}
