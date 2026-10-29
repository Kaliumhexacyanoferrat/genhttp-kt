package org.genhttp.engine.ioxide.infrastructure.endpoints

import ioxide.tls.TlsOptions
import ioxide.tls.TlsProtocolVersion

import org.genhttp.api.infrastructure.SslPolicyError
import org.genhttp.api.infrastructure.SslProtocol
import org.genhttp.api.infrastructure.certificates.ObjectCertificateProvider
import org.genhttp.engine.ioxide.TcpTransportOptions
import org.genhttp.engine.ioxide.TlsProvider
import org.genhttp.engine.ioxide.context.ConnectionContext
import org.genhttp.engine.ioxide.infrastructure.IoxideServer
import org.genhttp.engine.ioxide.security.BoringSslLibrary
import org.genhttp.engine.ioxide.security.BoringSslTls
import org.genhttp.engine.ioxide.security.CertificatePem
import org.genhttp.engine.ioxide.security.JdkCiphers
import org.genhttp.engine.ioxide.security.JdkTls
import org.genhttp.engine.ioxide.security.ProviderKeyManager
import org.genhttp.engine.ioxide.security.TlsTransport
import org.genhttp.engine.ioxide.security.ValidatingTrustManager
import org.genhttp.engine.shared.infrastructure.SecurityConfiguration
import org.genhttp.engine.shared.security.SimpleCertificateProvider
import org.genhttp.engine.shared.types.ConnectionExceptions

import java.net.InetAddress
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine

/**
 * An endpoint serving HTTPS, terminating TLS with BoringSSL (through ioxide) or the JDK (see [TlsProvider]).
 */
internal class SecureEndPoint(
    server: IoxideServer,
    address: InetAddress?,
    port: Int,
    dualStack: Boolean,
    val options: SecurityConfiguration
) : SocketEndPoint(server, address, port, dualStack) {

    private val tcp: TcpTransportOptions = server.options.tcp

    /**
     * The implementation TLS is terminated with.
     */
    val provider: TlsProvider = resolve(tcp.tlsProvider)

    private val context: SSLContext? = if (provider == TlsProvider.Jdk) createContext() else null

    private val ciphers: Array<String>? = context?.let { JdkCiphers.select(tcp.cipherSuites, tcp.cipherList, it) }

    override val secure: Boolean
        get() = true

    // region Functionality

    /**
     * The options of the TLS service that terminates the connections of this endpoint on every reactor, if they are
     * terminated with BoringSSL.
     */
    fun tlsOptions(): TlsOptions? {
        if (provider != TlsProvider.BoringSsl) {
            return null
        }

        val certificate = options.certificateProvider.provide(null)
            ?: throw IllegalStateException("The provider did not return a certificate to be used for port $port")

        return TlsOptions(
            certificatePem = CertificatePem.chain(certificate),
            keyPem = CertificatePem.key(certificate),
            alpn = listOf("http/1.1"),
            handshakeTimeoutMs = tcp.handshakeTimeoutMs,
            minProtocolVersion = minProtocolVersion(),
            cipherSuites = tcp.cipherSuites,
            cipherList = tcp.cipherList,
            kernelTx = tcp.txKernelTls,
            kernelRx = tcp.rxKernelTls
        )
    }

    /**
     * Performs the handshake with the client of the given connection and authenticates it (on the reactor thread).
     *
     * @return The TLS session of the connection, or null if the client has not been accepted
     */
    suspend fun accept(connection: ConnectionContext): TlsTransport? {
        try {
            return if (provider == TlsProvider.BoringSsl) acceptBoringSsl(connection) else acceptJdk(connection)
        } catch (e: Exception) {
            if (!ConnectionExceptions.isGracefulDisconnect(e)) {
                logger.warn("Failed to authenticate client connection", e)
            }

            return null
        }
    }

    private suspend fun acceptBoringSsl(connection: ConnectionContext): TlsTransport {
        val service = connection.tlsService(port) ?: throw IllegalStateException("No TLS service has been started for port $port")

        // the service enforces the handshake timeout itself
        return BoringSslTls(service.accept(connection.connection))
    }

    private suspend fun acceptJdk(connection: ConnectionContext): TlsTransport? {
        val tls = JdkTls(createEngine(), connection.outbound)

        connection.handshakeStarted(tcp.handshakeTimeoutMs)

        try {
            val completed = try {
                tls.handshake(connection.inbound)
            } catch (e: Exception) {
                tls.abort()
                connection.outbound.flush()
                throw e
            }

            if (!completed || !authenticate(tls.clientCertificate)) {
                tls.close(connection.outbound)
                connection.outbound.flush()

                return null
            }

            return tls
        } finally {
            connection.handshakeCompleted()
        }
    }

    // the trust manager is not invoked if the client does not send a certificate
    private fun authenticate(certificate: X509Certificate?): Boolean {
        val validator = options.certificateValidator

        return validator == null || certificate != null || validator.validate(null, null, setOf(SslPolicyError.RemoteCertificateNotAvailable))
    }

    private fun createContext(): SSLContext = SSLContext.getInstance("TLS").apply {
        val trustManagers = options.certificateValidator?.let { arrayOf(ValidatingTrustManager(it)) }
        init(arrayOf(ProviderKeyManager(options.certificateProvider)), trustManagers, null)
    }

    private fun createEngine(): SSLEngine = context!!.createSSLEngine().apply {
        useClientMode = false
        enabledProtocols = options.protocols.map { it.protocolName }.toTypedArray()

        val parameters = sslParameters

        parameters.applicationProtocols = arrayOf("http/1.1")

        ciphers?.let { parameters.cipherSuites = it }

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

    // BoringSSL takes a minimum version and has no maximum (like the ioxide engine of GenHTTP)
    private fun minProtocolVersion(): TlsProtocolVersion {
        val tls12 = SslProtocol.Tls12 in options.protocols
        val tls13 = SslProtocol.Tls13 in options.protocols

        if (tcp.txKernelTls) {
            // kernel TLS pins TLS 1.3 itself
            return TlsProtocolVersion.DEFAULT
        }

        if (tls12 && !tls13) {
            logger.warn("Port {} was bound for TLS 1.2 without 1.3. BoringSSL takes a minimum version and has no maximum, so 1.3 stays available on this endpoint - it is negotiated only when the client prefers it, and it is the stronger of the two.", port)
        }

        return when {
            tls13 && !tls12 -> TlsProtocolVersion.TLS13
            tls12 -> TlsProtocolVersion.TLS12
            else -> TlsProtocolVersion.DEFAULT
        }
    }

    private fun resolve(requested: TlsProvider): TlsProvider {
        val supported = supportedByBoringSsl()

        return when (requested) {
            TlsProvider.Auto -> {
                if (BoringSslLibrary.available && supported == null) {
                    TlsProvider.BoringSsl
                } else {
                    if (tcp.txKernelTls) {
                        logger.warn("Kernel TLS requires BoringSSL, which cannot serve port {} ({}), the JDK terminates its TLS without it", port,
                            supported ?: BoringSslLibrary.unavailabilityCause?.message)
                    }

                    TlsProvider.Jdk
                }
            }

            TlsProvider.BoringSsl -> {
                BoringSslLibrary.unavailabilityCause?.let { throw UnsupportedOperationException("BoringSSL is not available: ${it.message}", it) }

                if (supported != null) {
                    throw UnsupportedOperationException("BoringSSL cannot serve port $port: $supported")
                }

                TlsProvider.BoringSsl
            }

            TlsProvider.Jdk -> {
                if (tcp.txKernelTls) {
                    throw UnsupportedOperationException("Kernel TLS requires the BoringSSL provider")
                }

                TlsProvider.Jdk
            }
        }
    }

    // why BoringSSL cannot serve the endpoint (ioxide's Kotlin port does not select certificates per host or validate
    // client certificates yet), or null if it can
    private fun supportedByBoringSsl(): String? = when {
        options.certificateValidator != null -> "client certificates are validated by the JDK provider only"
        !servesOneCertificate() -> "certificates per host are selected by the JDK provider only"
        tcp.txKernelTls && SslProtocol.Tls13 !in options.protocols -> "kernel TLS requires TLS 1.3"
        else -> null
    }

    // the providers known to return the same certificate for every host (subclasses of the open one may not)
    private fun servesOneCertificate(): Boolean {
        val provider = options.certificateProvider

        return provider is SimpleCertificateProvider || provider.javaClass == ObjectCertificateProvider::class.java
    }

    override fun describeSettings(): String =
        "${super.describeSettings()}, TLS: $provider${if (provider == TlsProvider.BoringSsl && tcp.txKernelTls) " (kTLS)" else ""}, " +
                "Protocols: ${options.protocols.joinToString { it.protocolName }}, " +
                "ClientCertificate: ${if (options.certificateValidator?.requireCertificate == true) "Required" else "Optional"}"

    // endregion

}
