package org.genhttp.engine.ioxide.security

import java.security.KeyStore
import java.util.Base64

/**
 * The PEM text BoringSSL loads a certificate and its key from (like `ExportChainPem` and `ExportKeyPem` of the ioxide
 * engine in GenHTTP).
 */
internal object CertificatePem {

    private val encoder = Base64.getMimeEncoder(64, "\n".toByteArray(Charsets.US_ASCII))

    /**
     * The certificate chain of the given entry, leaf first.
     */
    fun chain(entry: KeyStore.PrivateKeyEntry): String =
        entry.certificateChain.joinToString("") { block("CERTIFICATE", it.encoded) }

    /**
     * The private key of the given entry, as PKCS#8.
     */
    fun key(entry: KeyStore.PrivateKeyEntry): String {
        val key = entry.privateKey

        require(key.format == "PKCS#8" && key.encoded != null) { "The private key of the certificate cannot be exported (format: ${key.format})" }

        return block("PRIVATE KEY", key.encoded)
    }

    private fun block(type: String, der: ByteArray): String =
        "-----BEGIN $type-----\n${encoder.encodeToString(der)}\n-----END $type-----\n"

}
