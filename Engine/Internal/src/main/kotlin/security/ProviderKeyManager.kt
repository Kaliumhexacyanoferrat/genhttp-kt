package org.genhttp.engine.internal.security

import org.genhttp.api.infrastructure.CertificateProvider

import java.net.Socket
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Principal
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.ExtendedSSLSession
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.StandardConstants
import javax.net.ssl.X509ExtendedKeyManager

/**
 * Selects the server certificate per requested host (SNI) using a [CertificateProvider].
 */
internal class ProviderKeyManager(private val provider: CertificateProvider) : X509ExtendedKeyManager() {

    private val entries = ConcurrentHashMap<String, KeyStore.PrivateKeyEntry>()

    override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out Principal>?, engine: SSLEngine?): String? =
        choose(keyType, engine?.handshakeSession)

    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? =
        choose(keyType, (socket as? SSLSocket)?.handshakeSession)

    private fun choose(keyType: String?, session: SSLSession?): String? {
        val host = (session as? ExtendedSSLSession)?.requestedServerNames
            ?.firstOrNull { it.type == StandardConstants.SNI_HOST_NAME }
            ?.let { (it as? SNIHostName)?.asciiName }

        val entry = provider.provide(host)
            ?: throw IllegalStateException("The provider did not return a certificate to be used for host '$host'")

        if (!matches(entry.privateKey.algorithm, keyType)) {
            return null
        }

        val alias = aliasOf(entry)
        entries[alias] = entry

        return alias
    }

    override fun getCertificateChain(alias: String?): Array<X509Certificate>? =
        entries[alias]?.certificateChain?.map { it as X509Certificate }?.toTypedArray()

    override fun getPrivateKey(alias: String?): PrivateKey? = entries[alias]?.privateKey

    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String> = entries.keys.toTypedArray()

    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null

    override fun chooseClientAlias(keyType: Array<out String>?, issuers: Array<out Principal>?, socket: Socket?): String? = null

    private companion object {

        fun matches(keyAlgorithm: String, keyType: String?): Boolean {
            if (keyType == null) {
                return true
            }

            return when (keyAlgorithm.uppercase()) {
                "RSA" -> keyType.startsWith("RSA", ignoreCase = true)
                "EC" -> keyType.equals("EC", ignoreCase = true) || keyType.startsWith("ECDSA", ignoreCase = true)
                else -> keyAlgorithm.equals(keyType, ignoreCase = true)
            }
        }

        fun aliasOf(entry: KeyStore.PrivateKeyEntry): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(entry.certificate.encoded)
            return "genhttp-" + HexFormat.of().formatHex(digest)
        }

    }

}
