package org.genhttp.api.infrastructure.certificates

import org.genhttp.api.infrastructure.CertificateProvider

import java.security.KeyStore

/**
 * Provides the given certificate for every requested host.
 *
 * @param certificate The certificate to be used to secure the endpoint
 */
public open class ObjectCertificateProvider(private val certificate: KeyStore.PrivateKeyEntry) : CertificateProvider {

    override fun provide(host: String?): KeyStore.PrivateKeyEntry = certificate

}
