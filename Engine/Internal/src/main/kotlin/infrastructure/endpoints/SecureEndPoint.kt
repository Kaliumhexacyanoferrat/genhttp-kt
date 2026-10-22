package org.genhttp.engine.internal.infrastructure.endpoints

import org.genhttp.api.infrastructure.Server
import org.genhttp.api.infrastructure.SslPolicyError
import org.genhttp.engine.internal.infrastructure.ConnectionWatch
import org.genhttp.engine.internal.infrastructure.ConnectionWatchdog
import org.genhttp.engine.internal.security.ProviderKeyManager
import org.genhttp.engine.internal.security.TlsChannel
import org.genhttp.engine.internal.security.ValidatingTrustManager
import org.genhttp.engine.shared.infrastructure.SecurityConfiguration
import org.genhttp.engine.shared.types.ConnectionExceptions

import java.net.InetAddress
import java.nio.channels.SocketChannel
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLPeerUnverifiedException

import kotlin.time.Duration.Companion.seconds

/**
 * An endpoint serving HTTPS, using an SSL engine on top of the socket channel.
 */
internal class SecureEndPoint(
    server: Server,
    watchdog: ConnectionWatchdog,
    address: InetAddress?,
    port: Int,
    dualStack: Boolean,
    val options: SecurityConfiguration
) : SocketEndPoint(server, watchdog, address, port, dualStack) {

    private val context: SSLContext = SSLContext.getInstance("TLS").apply {
        val trustManagers = options.certificateValidator?.let { arrayOf(ValidatingTrustManager(it)) }
        init(arrayOf(ProviderKeyManager(options.certificateProvider)), trustManagers, null)
    }

    override val secure: Boolean
        get() = true

    override suspend fun accept(client: SocketChannel, watch: ConnectionWatch) {
        val channel = tryAuthenticate(client, watch)

        if (channel != null) {
            handle(client, channel.channel, watch, channel.certificate)
        } else {
            try {
                client.close()
            } catch (e: Exception) {
                if (!ConnectionExceptions.isGracefulDisconnect(e)) {
                    logger.warn("Failed to close unauthenticated client connection", e)
                }
            }
        }
    }

    private fun tryAuthenticate(client: SocketChannel, watch: ConnectionWatch): AuthenticatedChannel? {
        try {
            val engine = context.createSSLEngine().apply {
                useClientMode = false
                enabledProtocols = options.protocols.map { it.protocolName }.toTypedArray()

                val parameters = sslParameters
                parameters.applicationProtocols = arrayOf("http/1.1")

                val validator = options.certificateValidator

                if (validator != null) {
                    if (validator.requireCertificate) {
                        parameters.needClientAuth = true
                    } else {
                        parameters.wantClientAuth = true
                    }
                }

                sslParameters = parameters
            }

            val channel = TlsChannel(client, engine)

            if (!watch.arm(ConnectionWatch.deadlineAfter(HandshakeTimeout))) {
                return null
            }

            try {
                channel.handshake()
            } finally {
                watch.disarm()
            }

            if (watch.expired) {
                return null
            }

            val certificate = try {
                engine.session.peerCertificates.firstOrNull() as? X509Certificate
            } catch (_: SSLPeerUnverifiedException) {
                null
            }

            val validator = options.certificateValidator

            if (validator != null && certificate == null) {
                // the trust manager is not invoked if the client does not send a certificate
                if (!validator.validate(null, null, setOf(SslPolicyError.RemoteCertificateNotAvailable))) {
                    channel.close()
                    return null
                }
            }

            return AuthenticatedChannel(channel, certificate)
        } catch (e: Exception) {
            if (!watch.expired && !ConnectionExceptions.isGracefulDisconnect(e)) {
                logger.warn("Failed to authenticate client connection", e)
            }

            return null
        }
    }

    override fun describeSettings(): String =
        "${super.describeSettings()}, Protocols: ${options.protocols.joinToString { it.protocolName }}, " +
                "ClientCertificate: ${if (options.certificateValidator?.requireCertificate == true) "Required" else "Optional"}"

    private class AuthenticatedChannel(val channel: TlsChannel, val certificate: X509Certificate?)

    private companion object {

        val HandshakeTimeout = 10.seconds

    }

}
