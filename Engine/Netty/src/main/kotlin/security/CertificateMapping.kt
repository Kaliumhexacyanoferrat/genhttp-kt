package org.genhttp.engine.netty.security

import io.netty.handler.ssl.ApplicationProtocolConfig
import io.netty.handler.ssl.ClientAuth
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslProvider
import io.netty.util.Mapping

import org.genhttp.engine.shared.infrastructure.SecurityConfiguration

import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap

/**
 * Selects the TLS context per requested host (SNI) using the certificate provider of a secure endpoint.
 *
 * A context is built once per certificate returned by the provider and reused for all handshakes that are answered
 * with this certificate.
 */
internal class CertificateMapping(private val security: SecurityConfiguration) : Mapping<String?, SslContext> {

    private val contexts = ConcurrentHashMap<String, SslContext>()

    private val trustManager = security.certificateValidator?.let { ValidatingTrustManager(it) }

    @Volatile
    private var last: Pair<KeyStore.PrivateKeyEntry, SslContext>? = null

    override fun map(input: String?): SslContext {
        val entry = security.certificateProvider.provide(input)
            ?: throw IllegalStateException("The provider did not return a certificate to be used for host '$input'")

        val recent = last

        if (recent != null && recent.first === entry) {
            return recent.second
        }

        val context = contexts.computeIfAbsent(fingerprintOf(entry)) { build(entry) }

        last = Pair(entry, context)

        return context
    }

    private fun build(entry: KeyStore.PrivateKeyEntry): SslContext {
        val chain = entry.certificateChain.map { it as X509Certificate }

        val builder = SslContextBuilder.forServer(entry.privateKey, chain)
            .sslProvider(SslProvider.JDK)
            .protocols(security.protocols.map { it.protocolName })
            .applicationProtocolConfig(Http1Only)

        val validator = security.certificateValidator

        if (validator != null) {
            builder.trustManager(trustManager)
                .clientAuth(if (validator.requireCertificate) ClientAuth.REQUIRE else ClientAuth.OPTIONAL)
        }

        return builder.build()
    }

    private companion object {

        val Http1Only = ApplicationProtocolConfig(
            ApplicationProtocolConfig.Protocol.ALPN,
            ApplicationProtocolConfig.SelectorFailureBehavior.NO_ADVERTISE,
            ApplicationProtocolConfig.SelectedListenerFailureBehavior.ACCEPT,
            "http/1.1"
        )

        fun fingerprintOf(entry: KeyStore.PrivateKeyEntry): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(entry.certificate.encoded)
            return HexFormat.of().formatHex(digest)
        }

    }

}
