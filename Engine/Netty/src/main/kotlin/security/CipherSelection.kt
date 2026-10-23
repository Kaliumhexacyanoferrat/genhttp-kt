package org.genhttp.engine.netty.security

import io.netty.handler.ssl.CipherSuiteConverter
import io.netty.handler.ssl.CipherSuiteFilter
import io.netty.handler.ssl.OpenSsl
import io.netty.handler.ssl.SslProvider

import javax.net.ssl.SSLContext

/**
 * Selects the cipher suites offered by a secure endpoint (`CipherSuites` for TLS 1.3 and `CipherList` for TLS 1.2
 * of ioxide). Names are accepted in OpenSSL format or as the IANA names used by the JDK; suites that are not
 * configured keep the defaults of the provider.
 */
internal class CipherSelection private constructor(private val tls13: List<String>?, private val tls12: List<String>?) : CipherSuiteFilter {

    override fun filterCipherSuites(ciphers: Iterable<String>?, defaultCiphers: List<String>, supportedCiphers: Set<String>): Array<String> {
        val selected = (tls13 ?: defaultCiphers.filter(::isTls13)) + (tls12 ?: defaultCiphers.filterNot(::isTls13))

        return selected.filter { it in supportedCiphers }.distinct().toTypedArray()
    }

    companion object {

        /**
         * Creates the selection for the given options, or returns null if the defaults of the provider are used.
         *
         * @throws IllegalArgumentException if a configured suite is not supported by the provider
         */
        fun of(cipherSuites: String?, cipherList: String?, provider: SslProvider): CipherSelection? {
            if (cipherSuites == null && cipherList == null) {
                return null
            }

            val tls13 = cipherSuites?.let(::parse)
            val tls12 = cipherList?.let(::parse)

            val supported = if (provider == SslProvider.JDK) {
                SSLContext.getDefault().supportedSSLParameters.cipherSuites.toSet()
            } else {
                OpenSsl.availableJavaCipherSuites()
            }

            // reported as configured, not as converted
            val unsupported = (tls13.orEmpty() + tls12.orEmpty()).filter { it.java !in supported }.map { it.configured }

            require(unsupported.isEmpty()) { "Cipher suites not supported by the TLS provider ($provider): ${unsupported.joinToString()}" }

            return CipherSelection(tls13?.map { it.java }, tls12?.map { it.java })
        }

        private fun parse(names: String): List<Suite> =
            names.split(':').map { it.trim() }.filter { it.isNotEmpty() }.map { Suite(it, CipherSuiteConverter.toJava(it, "TLS") ?: it) }

        // the IANA names of TLS 1.3 suites do not name the key exchange (TLS_AES_128_GCM_SHA256)
        private fun isTls13(name: String) = name.startsWith("TLS_") && !name.contains("_WITH_")

    }

    /**
     * A suite as configured and with its name in the notation of the JDK.
     */
    private class Suite(val configured: String, val java: String)

}
