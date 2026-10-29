package org.genhttp.engine.ioxide.security

import javax.net.ssl.SSLContext

/**
 * Selects the cipher suites the JDK provider offers (`CipherSuites` for TLS 1.3 and `CipherList` for TLS 1.2 of
 * ioxide), as the IANA names used by the JDK; suites that are not configured keep the defaults of the provider.
 */
internal object JdkCiphers {

    /**
     * The suites to be enabled for the given options, or null if the defaults of the provider are used.
     *
     * @throws IllegalArgumentException if a configured suite is not supported by the provider
     */
    fun select(cipherSuites: String?, cipherList: String?, context: SSLContext): Array<String>? {
        if (cipherSuites == null && cipherList == null) {
            return null
        }

        val tls13 = cipherSuites?.let(::parse)
        val tls12 = cipherList?.let(::parse)

        val supported = context.supportedSSLParameters.cipherSuites.toSet()

        val unsupported = (tls13.orEmpty() + tls12.orEmpty()).filter { it !in supported }

        require(unsupported.isEmpty()) { "Cipher suites not supported by the TLS provider (JDK): ${unsupported.joinToString()}" }

        val defaults = context.defaultSSLParameters.cipherSuites.toList()

        return ((tls13 ?: defaults.filter(::isTls13)) + (tls12 ?: defaults.filterNot(::isTls13))).distinct().toTypedArray()
    }

    private fun parse(names: String): List<String> = names.split(':').map { it.trim() }.filter { it.isNotEmpty() }

    // the IANA names of TLS 1.3 suites do not name the key exchange (TLS_AES_128_GCM_SHA256)
    private fun isTls13(name: String) = name.startsWith("TLS_") && !name.contains("_WITH_")

}
