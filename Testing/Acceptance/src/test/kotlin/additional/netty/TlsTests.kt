package org.genhttp.testing.acceptance.additional.netty

import kotlinx.coroutines.future.await

import org.genhttp.api.infrastructure.CertificateProvider
import org.genhttp.api.infrastructure.CertificateValidator
import org.genhttp.api.infrastructure.SslPolicyError
import org.genhttp.api.infrastructure.X509RevocationMode
import org.genhttp.api.protocol.ContentType
import org.genhttp.engine.netty.EngineOptions
import org.genhttp.engine.netty.TcpTransportOptions
import org.genhttp.engine.netty.TlsProvider
import org.genhttp.modules.io.content
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.AsyncFunctionalHandler
import org.genhttp.testing.acceptance.utilities.Security
import org.genhttp.testing.getContent

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

import org.slf4j.event.Level

import java.io.BufferedInputStream
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.CopyOnWriteArrayList
import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Not ported from GenHTTP: TLS on the Netty engine is terminated by OpenSSL (BoringSSL) or by the JDK, the automatic
 * choice prefers OpenSSL. Both providers serve HTTPS (sending large responses completely, also before closing the
 * connection) and validate client certificates. The TLS 1.2 suites can be restricted with both providers (names in
 * OpenSSL or IANA notation), the TLS 1.3 suites with the JDK provider only (BoringSSL does not allow to restrict
 * them, which is logged). Suites the provider does not support stop the server from starting. Where OpenSSL is not
 * available, requesting it stops the server from starting and the automatic choice falls back to the JDK. The test
 * certificate has an RSA key.
 */
class TlsTests {

    // region Providers

    @Test
    fun testJdkProvider() = test {
        assertServes(TlsProvider.Jdk, "JDK")
    }

    @Test
    fun testOpenSslProvider() = test {
        withProvider(TlsProvider.OpenSsl) {
            assertServes(TlsProvider.OpenSsl, "OPENSSL")
        }
    }

    @Test
    fun testAutoPrefersOpenSsl() = test {
        // falls back to the JDK where OpenSSL is not available
        assertServes(TlsProvider.Auto, if (Natives.openSsl) "OPENSSL" else "JDK")
    }

    // endregion

    // region Ciphers

    @ParameterizedTest
    @EnumSource(TlsProvider::class, names = ["Jdk", "OpenSsl"])
    fun testCipherListRestrictsTls12(provider: TlsProvider) = test {
        withProvider(provider) {
            secure(TcpTransportOptions(tlsProvider = provider, cipherList = "ECDHE-RSA-AES128-GCM-SHA256")).use { server ->
                assertEquals("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256", negotiate(server.port, "TLSv1.2"))

                assertFailsWith<IOException> { negotiate(server.port, "TLSv1.2", "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384") }
            }

            secure(TcpTransportOptions(tlsProvider = provider, cipherList = "ECDHE-RSA-AES256-GCM-SHA384:ECDHE-RSA-CHACHA20-POLY1305")).use { server ->
                assertEquals("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384", negotiate(server.port, "TLSv1.2", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256", "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384"))
                assertEquals("TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256", negotiate(server.port, "TLSv1.2", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256", "TLS_ECDHE_RSA_WITH_CHACHA20_POLY1305_SHA256"))

                assertFailsWith<IOException> { negotiate(server.port, "TLSv1.2", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256") }

                // TLS 1.3 keeps the defaults of the provider
                negotiate(server.port, "TLSv1.3")
            }
        }
    }

    @ParameterizedTest
    @EnumSource(TlsProvider::class, names = ["Jdk", "OpenSsl"])
    fun testCipherListAcceptsIanaNames(provider: TlsProvider) = test {
        withProvider(provider) {
            secure(TcpTransportOptions(tlsProvider = provider, cipherList = "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384")).use { server ->
                assertEquals("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384", negotiate(server.port, "TLSv1.2"))

                assertFailsWith<IOException> { negotiate(server.port, "TLSv1.2", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256") }
            }
        }
    }

    @Test
    fun testCipherSuitesRestrictTls13() = test {
        secure(TcpTransportOptions(tlsProvider = TlsProvider.Jdk, cipherSuites = "TLS_CHACHA20_POLY1305_SHA256")).use { server ->
            assertEquals("TLS_CHACHA20_POLY1305_SHA256", negotiate(server.port, "TLSv1.3"))

            assertFailsWith<IOException> { negotiate(server.port, "TLSv1.3", "TLS_AES_256_GCM_SHA384") }

            // TLS 1.2 keeps the defaults of the provider
            negotiate(server.port, "TLSv1.2")
        }
    }

    @Test
    fun testCipherSuitesAreNotRestrictedByBoringSsl() = test {
        withProvider(TlsProvider.OpenSsl) {
            secure(TcpTransportOptions(tlsProvider = TlsProvider.OpenSsl, cipherSuites = "TLS_CHACHA20_POLY1305_SHA256")).use { server ->
                assertEquals("TLS_AES_256_GCM_SHA384", negotiate(server.port, "TLSv1.3", "TLS_AES_256_GCM_SHA384"))

                val warning = "BoringSSL does not allow to configure the TLS 1.3 cipher suites, they are not restricted to 'TLS_CHACHA20_POLY1305_SHA256'"

                assertTrue(server.runner.logging.entries.any { it.level == Level.WARN && it.message == warning }, "Warning not logged: ${server.runner.logging.messages}")
            }
        }
    }

    @ParameterizedTest
    @EnumSource(TlsProvider::class, names = ["Jdk", "OpenSsl"])
    fun testUnsupportedCiphersStopTheStart(provider: TlsProvider) = test {
        withProvider(provider) {
            val name = if (provider == TlsProvider.Jdk) "JDK" else "OPENSSL"

            val tls12 = assertFailsWith<IllegalArgumentException> {
                secure(TcpTransportOptions(tlsProvider = provider, cipherList = "ECDHE-RSA-AES128-GCM-SHA256:TLS_ECDHE_RSA_WITH_NOT_A_CIPHER"))
            }

            assertEquals("Cipher suites not supported by the TLS provider ($name): TLS_ECDHE_RSA_WITH_NOT_A_CIPHER", tls12.message)

            val tls13 = assertFailsWith<IllegalArgumentException> {
                secure(TcpTransportOptions(tlsProvider = provider, cipherSuites = "TLS_NOT_A_SUITE"))
            }

            assertEquals("Cipher suites not supported by the TLS provider ($name): TLS_NOT_A_SUITE", tls13.message)
        }
    }

    // endregion

    // region Large responses

    @ParameterizedTest
    @EnumSource(TlsProvider::class, names = ["Jdk", "OpenSsl"])
    fun testLargeResponsesAreSentCompletely(provider: TlsProvider) = test {
        withProvider(provider) {
            secure(TcpTransportOptions(tlsProvider = provider)).use { server ->
                RawClient.connectSecure(server.port, receiveBuffer = 8192).use { socket ->
                    val input = BufferedInputStream(socket.getInputStream())

                    RawClient.send(socket, RawClient.request("/large"))

                    assertTrue(Large.contentEquals(RawClient.readResponse(input).body))

                    // the connection is closed after the response has been sent
                    RawClient.send(socket, RawClient.request("/large", headers = listOf("Connection" to "close")))

                    assertTrue(Large.contentEquals(RawClient.readResponse(input).body))

                    assertEquals(-1, input.read())
                }
            }
        }
    }

    // endregion

    // region Client certificates

    @ParameterizedTest
    @EnumSource(TlsProvider::class, names = ["Jdk", "OpenSsl"])
    fun testClientCertificatesAreValidated(provider: TlsProvider) = test {
        withProvider(provider) {
            for (revocation in X509RevocationMode.entries) {
                val validator = RecordingValidator(true, revocation)

                secure(TcpTransportOptions(tlsProvider = provider), validator).use { server ->
                    val response = getWithCertificate(server.port)

                    assertEquals(200, response.statusCode())
                    assertEquals(Subject, response.getContent())
                }

                assertEquals(listOf(Validation), validator.calls, "Unexpected validation with $revocation")
            }
        }
    }

    @ParameterizedTest
    @EnumSource(TlsProvider::class, names = ["Jdk", "OpenSsl"])
    fun testRejectedClientCertificateFailsTheHandshake(provider: TlsProvider) = test {
        withProvider(provider) {
            val validator = RecordingValidator(false, X509RevocationMode.NoCheck)

            secure(TcpTransportOptions(tlsProvider = provider), validator).use { server ->
                assertFailsWith<IOException> { getWithCertificate(server.port) }
            }

            // the client retries the request after the server rejected its certificate
            assertTrue(validator.calls.isNotEmpty())
            assertTrue(validator.calls.all { it == Validation }, "Unexpected validation: ${validator.calls}")
        }
    }

    // endregion

    // region Helpers

    /**
     * Runs the given checks with the given provider. OpenSSL is not shipped for every platform (not for Windows on
     * ARM64), where it is not available a server that requests it explicitly must not start (instead of silently
     * using the JDK).
     */
    private suspend fun withProvider(provider: TlsProvider, checks: suspend () -> Unit) {
        if (provider == TlsProvider.OpenSsl && !Natives.openSsl) {
            val e = assertFailsWith<UnsupportedOperationException> { secure(TcpTransportOptions(tlsProvider = TlsProvider.OpenSsl)) }

            assertEquals("OpenSSL is not available on this system", e.message)
        } else {
            checks()
        }
    }

    /**
     * A server with a secure endpoint (on [port]).
     */
    private class SecureServer(val runner: NettyRunner, val port: Int) : AutoCloseable {

        override fun close() = runner.close()

    }

    /**
     * Starts a server with a secure endpoint that answers with the subject of the client certificate (if any), or
     * with [Large] data.
     */
    private suspend fun secure(options: TcpTransportOptions, validator: CertificateValidator? = null): SecureServer {
        val handler = AsyncFunctionalHandler { request ->
            if (request.header.path.toString() == "/large") {
                request.respond().content(Large, ContentType.ApplicationOctetStream).build()
            } else {
                request.respond().content(request.client.certificate?.subjectX500Principal?.name ?: "Hello World!").build()
            }
        }

        val runner = NettyRunner(handler, EngineOptions(tcp = options))

        val port = TestHost.nextPort()

        try {
            runner.host.bind(null, port, CertificateProvider.from(Security.getCertificate()), certificateValidator = validator)
            runner.start()
        } catch (e: Throwable) {
            runner.close()
            throw e
        }

        return SecureServer(runner, port)
    }

    private suspend fun assertServes(provider: TlsProvider, expected: String) {
        secure(TcpTransportOptions(tlsProvider = provider)).use { server ->
            val request = HttpRequest.newBuilder(URI.create("https://localhost:${server.port}/")).GET().build()

            repeat(3) {
                val response = TestHost.getClient(ignoreSecurityErrors = true).sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).await()

                assertEquals(200, response.statusCode())
                assertEquals("Hello World!", response.getContent())
            }

            assertTrue(server.runner.logging.messages.any { it.contains("TLS: $expected") }, "'TLS: $expected' not found in ${server.runner.logging.messages}")

            // both protocols are offered by default
            negotiate(server.port, "TLSv1.2")
            negotiate(server.port, "TLSv1.3")
        }
    }

    /**
     * Connects with the given protocol (and cipher suites, all supported by default), sends a request and returns
     * the negotiated cipher suite.
     */
    private fun negotiate(port: Int, protocol: String, vararg suites: String): String {
        val socket = RawClient.connectSecure(port) { configured ->
            configured.enabledProtocols = arrayOf(protocol)

            if (suites.isNotEmpty()) {
                configured.enabledCipherSuites = arrayOf(*suites)
            }
        }

        socket.use {
            RawClient.send(socket, RawClient.request(headers = listOf("Connection" to "close")))

            assertEquals("Hello World!", RawClient.readResponse(socket.getInputStream()).text)

            return socket.session.cipherSuite
        }
    }

    private suspend fun getWithCertificate(port: Int): HttpResponse<ByteArray> {
        val certificate = Security.getCertificate()

        val store = KeyStore.getInstance("PKCS12")
        store.load(null, null)
        store.setKeyEntry("client", certificate.privateKey, CharArray(0), certificate.certificateChain)

        val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        factory.init(store, CharArray(0))

        val keyManagers: Array<KeyManager> = factory.keyManagers

        val context = SSLContext.getInstance("TLS")
        context.init(keyManagers, arrayOf(RawClient.TrustAll), SecureRandom())

        val client = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .sslContext(context)
            .build()

        val request = HttpRequest.newBuilder(URI.create("https://localhost:$port/")).GET().build()

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).await()
    }

    /**
     * Requires a client certificate and records what it is asked to validate.
     */
    private class RecordingValidator(private val verdict: Boolean, override val revocationCheck: X509RevocationMode) : CertificateValidator {

        val calls: MutableList<String> = CopyOnWriteArrayList()

        override val requireCertificate: Boolean
            get() = true

        override fun validate(certificate: X509Certificate?, chain: List<X509Certificate>?, policyErrors: Set<SslPolicyError>): Boolean {
            calls.add("${certificate?.subjectX500Principal?.name} ${policyErrors.sorted()}")
            return verdict
        }

    }

    private companion object {

        val Large = ByteArray(8 * 1024 * 1024) { it.toByte() }

        // the subject of the test certificate (RFC 2253)
        const val Subject = "CN=genhttp.local,O=GenHTTP"

        // what the validator is asked about the test certificate, which is self-signed and therefore not trusted
        const val Validation = "$Subject [RemoteCertificateChainErrors]"

    }

    // endregion

}
