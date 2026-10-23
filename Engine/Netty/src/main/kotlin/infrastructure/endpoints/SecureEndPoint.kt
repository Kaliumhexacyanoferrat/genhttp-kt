package org.genhttp.engine.netty.infrastructure.endpoints

import io.netty.buffer.ByteBufAllocator
import io.netty.channel.ChannelPipeline
import io.netty.handler.ssl.OpenSsl
import io.netty.handler.ssl.SniHandler
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslHandler
import io.netty.handler.ssl.SslProvider

import org.genhttp.api.infrastructure.SslPolicyError
import org.genhttp.engine.netty.infrastructure.NettyServer
import org.genhttp.engine.netty.security.CertificateMapping
import org.genhttp.engine.shared.infrastructure.SecurityConfiguration

import java.net.InetAddress
import java.security.cert.X509Certificate

/**
 * An endpoint serving HTTPS, terminating TLS with a handler that selects the certificate by the requested host.
 */
internal class SecureEndPoint(
    server: NettyServer,
    address: InetAddress?,
    port: Int,
    dualStack: Boolean,
    val options: SecurityConfiguration
) : SocketEndPoint(server, address, port, dualStack) {

    private val mapping = CertificateMapping(options, server.options.tcp)

    private val handshakeTimeout = server.options.tcp.handshakeTimeout.inWholeMilliseconds

    init {
        if (server.options.tcp.cipherSuites != null && mapping.provider == SslProvider.OPENSSL && OpenSsl.versionString().contains("BoringSSL")) {
            logger.warn("BoringSSL does not allow to configure the TLS 1.3 cipher suites, they are not restricted to '{}'", server.options.tcp.cipherSuites)
        }
    }

    override val secure: Boolean
        get() = true

    override fun configure(pipeline: ChannelPipeline) {
        pipeline.addLast(TlsHandler(mapping, handshakeTimeout))
    }

    override fun authenticate(certificate: X509Certificate?): Boolean {
        val validator = options.certificateValidator

        // the trust manager is not invoked if the client does not send a certificate
        return validator == null || certificate != null || validator.validate(null, null, setOf(SslPolicyError.RemoteCertificateNotAvailable))
    }

    override fun describeSettings(): String =
        "${super.describeSettings()}, TLS: ${mapping.provider}, Protocols: ${options.protocols.joinToString { it.protocolName }}, " +
                "ClientCertificate: ${if (options.certificateValidator?.requireCertificate == true) "Required" else "Optional"}"

    /**
     * Reads the requested host from the client hello and continues with the TLS context for this host.
     */
    private class TlsHandler(mapping: CertificateMapping, private val handshakeTimeout: Long) : SniHandler(mapping, handshakeTimeout) {

        override fun newSslHandler(context: SslContext, allocator: ByteBufAllocator): SslHandler =
            super.newSslHandler(context, allocator).apply {
                handshakeTimeoutMillis = handshakeTimeout
            }

    }

}
