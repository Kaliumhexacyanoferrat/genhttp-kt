package org.genhttp.engine.ioxide.security

import org.genhttp.api.infrastructure.CertificateValidator
import org.genhttp.api.infrastructure.SslPolicyError
import org.genhttp.api.infrastructure.X509RevocationMode

import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertPathBuilder
import java.security.cert.CertificateException
import java.security.cert.PKIXBuilderParameters
import java.security.cert.PKIXRevocationChecker
import java.security.cert.X509CertSelector
import java.security.cert.X509Certificate
import javax.net.ssl.CertPathTrustManagerParameters
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

/**
 * Validates client certificates by checking them against the JDK trust store and passing the result to a
 * [CertificateValidator].
 *
 * Same logic as the trust manager of the internal engine (the engines do not depend on each other).
 */
internal class ValidatingTrustManager(private val validator: CertificateValidator) : X509ExtendedTrustManager() {

    private val defaultTrustManager: X509TrustManager = createDefault(validator.revocationCheck)

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) = check(chain, authType)

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) = check(chain, authType)

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = check(chain, authType)

    private fun check(chain: Array<out X509Certificate>?, authType: String?) {
        val errors = mutableSetOf<SslPolicyError>()

        if (chain.isNullOrEmpty()) {
            errors.add(SslPolicyError.RemoteCertificateNotAvailable)
        } else {
            try {
                defaultTrustManager.checkClientTrusted(chain, authType ?: "UNKNOWN")
            } catch (_: CertificateException) {
                errors.add(SslPolicyError.RemoteCertificateChainErrors)
            }
        }

        if (!validator.validate(chain?.firstOrNull(), chain?.toList(), errors)) {
            throw CertificateException("The client certificate has been rejected")
        }
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, engine: SSLEngine?) = unsupported()

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?, socket: Socket?) = unsupported()

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = unsupported()

    /**
     * Do not restrict the issuers so that clients send any certificate they have, the validator decides.
     */
    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    private fun unsupported(): Nothing = throw CertificateException("Server certificates are not validated by this trust manager")

    private companion object {

        fun createDefault(revocation: X509RevocationMode): X509TrustManager {
            val factory = TrustManagerFactory.getInstance("PKIX")

            if (revocation == X509RevocationMode.NoCheck) {
                factory.init(null as KeyStore?)
            } else {
                val trustStore = loadDefaultTrustStore()

                val parameters = PKIXBuilderParameters(trustStore, X509CertSelector())

                val checker = CertPathBuilder.getInstance("PKIX").revocationChecker as PKIXRevocationChecker

                if (revocation == X509RevocationMode.Offline) {
                    checker.options = setOf(PKIXRevocationChecker.Option.PREFER_CRLS, PKIXRevocationChecker.Option.SOFT_FAIL)
                }

                parameters.addCertPathChecker(checker)
                parameters.isRevocationEnabled = false

                factory.init(CertPathTrustManagerParameters(parameters))
            }

            return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
        }

        fun loadDefaultTrustStore(): KeyStore {
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(null as KeyStore?)

            val issuers = factory.trustManagers.filterIsInstance<X509TrustManager>().first().acceptedIssuers

            val store = KeyStore.getInstance(KeyStore.getDefaultType())
            store.load(null, null)

            issuers.forEachIndexed { index, certificate -> store.setCertificateEntry("ca-$index", certificate) }

            return store
        }

    }

}
