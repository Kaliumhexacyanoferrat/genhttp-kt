package org.genhttp.testing.acceptance.additional.ioxide

import kotlinx.coroutines.future.await

import org.genhttp.api.infrastructure.CertificateProvider
import org.genhttp.api.infrastructure.CertificateValidator
import org.genhttp.api.infrastructure.SslPolicyError
import org.genhttp.api.infrastructure.X509RevocationMode
import org.genhttp.api.protocol.ContentType
import org.genhttp.engine.ioxide.EngineOptions
import org.genhttp.engine.ioxide.ReactorOptions
import org.genhttp.engine.ioxide.TcpTransportOptions
import org.genhttp.engine.ioxide.TlsProvider
import org.genhttp.modules.io.content
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.additional.netty.RawClient
import org.genhttp.testing.acceptance.utilities.AsyncFunctionalHandler
import org.genhttp.testing.acceptance.utilities.Security
import org.genhttp.testing.getContent

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

import org.slf4j.event.Level

import java.io.BufferedInputStream
import java.io.IOException
import java.lang.foreign.Arena
import java.lang.foreign.SymbolLookup
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Not ported from GenHTTP: TLS on the ioxide engine is terminated by BoringSSL (the native library of ioxide, with
 * optional kernel TLS) or by the JDK. The automatic choice prefers BoringSSL for the endpoints it can serve (one
 * certificate for every host, no client certificates) and uses the JDK for the others, which an explicit request for
 * BoringSSL refuses. Both providers serve HTTPS (sending large responses completely, also before closing the
 * connection), restrict the suites they offer (names of BoringSSL and IANA names respectively) and close connections
 * that do not complete their handshake in time; the JDK provider validates client certificates. Where BoringSSL is
 * not available, requesting it stops the server from starting and the automatic choice falls back to the JDK. The test
 * certificate has an RSA key.
 */
class TlsTests {

    // region Providers

    @Test
    fun testJdkProvider() = ioxideTest {
        assertServes(TlsProvider.Jdk, "Jdk")
    }

    @Test
    fun testBoringSslProvider() = ioxideTest {
        withBoringSsl {
            assertServes(TlsProvider.BoringSsl, "BoringSsl")
        }
    }

    @Test
    fun testAutoPrefersBoringSsl() = ioxideTest {
        // falls back to the JDK where BoringSSL is not available
        assertServes(TlsProvider.Auto, if (IoxideNatives.boringSsl) "BoringSsl" else "Jdk")
    }

    @Test
    fun testAutoUsesTheJdkForCertificatesPerHost() = ioxideTest {
        val certificate = Security.getCertificate()

        secure(TcpTransportOptions(), certificates = CertificateProvider { certificate }).use { server ->
            assertEquals("Hello World!", get(server.port).getContent())

            assertLogged(server, "TLS: Jdk")
        }
    }

    @Test
    fun testAutoUsesTheJdkForClientCertificates() = ioxideTest {
        secure(TcpTransportOptions(), RecordingValidator(true, X509RevocationMode.NoCheck)).use { server ->
            assertEquals(200, getWithCertificate(server.port).statusCode())

            assertLogged(server, "TLS: Jdk")
        }
    }

    @Test
    fun testBoringSslRefusesWhatItCannotServe() = ioxideTest {
        val validator = assertFailsWith<UnsupportedOperationException> {
            secure(TcpTransportOptions(tlsProvider = TlsProvider.BoringSsl), RecordingValidator(true, X509RevocationMode.NoCheck))
        }

        val certificate = Security.getCertificate()

        val perHost = assertFailsWith<UnsupportedOperationException> {
            secure(TcpTransportOptions(tlsProvider = TlsProvider.BoringSsl), certificates = CertificateProvider { certificate })
        }

        if (IoxideNatives.boringSsl) {
            assertTrue(validator.message!!.endsWith("client certificates are validated by the JDK provider only"), validator.message)
            assertTrue(perHost.message!!.endsWith("certificates per host are selected by the JDK provider only"), perHost.message)
        } else {
            assertTrue(validator.message!!.startsWith("BoringSSL is not available"), validator.message)
            assertTrue(perHost.message!!.startsWith("BoringSSL is not available"), perHost.message)
        }
    }

    @Test
    fun testKernelTlsRequiresBoringSsl() = ioxideTest {
        val e = assertFailsWith<UnsupportedOperationException> {
            secure(TcpTransportOptions(tlsProvider = TlsProvider.Jdk, txKernelTls = true))
        }

        assertEquals("Kernel TLS requires the BoringSSL provider", e.message)
    }

    @Test
    fun testKernelTls() = ioxideTest {
        withBoringSsl {
            secure(TcpTransportOptions(tlsProvider = TlsProvider.BoringSsl, txKernelTls = true)).use { server ->
                if (IoxideNatives.kernelTls) {
                    assertLogged(server, "TLS: BoringSsl (kTLS)")

                    // the kernel encrypts the records, TLS 1.3 with AES-128-GCM only
                    assertEquals("TLS_AES_128_GCM_SHA256", negotiate(server.port, "TLSv1.3"))

                    assertLarge(server.port)
                } else {
                    // without the tls kernel module, the handshake fails
                    assertFailsWith<IOException> { negotiate(server.port, "TLSv1.3") }
                }
            }
        }
    }

    @Test
    fun testAutoUsesTheJdkWithoutKernelTlsIfBoringSslCannotServe() = ioxideTest {
        val certificate = Security.getCertificate()

        secure(TcpTransportOptions(txKernelTls = true), certificates = CertificateProvider { certificate }).use { server ->
            assertEquals("Hello World!", get(server.port).getContent())

            assertTrue(server.runner.logging.entries.any { it.level == Level.WARN && it.message.startsWith("Kernel TLS requires BoringSSL") }, "Warning not logged: ${server.runner.logging.messages}")
        }
    }

    // endregion

    // region Ciphers

    @Test
    fun testJdkCipherListRestrictsTls12() = ioxideTest {
        secure(TcpTransportOptions(tlsProvider = TlsProvider.Jdk, cipherList = "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384")).use { server ->
            assertEquals("TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384", negotiate(server.port, "TLSv1.2"))

            assertFailsWith<IOException> { negotiate(server.port, "TLSv1.2", "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256") }

            // TLS 1.3 keeps the defaults of the provider
            negotiate(server.port, "TLSv1.3")
        }
    }

    @Test
    fun testJdkCipherSuitesRestrictTls13() = ioxideTest {
        secure(TcpTransportOptions(tlsProvider = TlsProvider.Jdk, cipherSuites = "TLS_CHACHA20_POLY1305_SHA256")).use { server ->
            assertEquals("TLS_CHACHA20_POLY1305_SHA256", negotiate(server.port, "TLSv1.3"))

            assertFailsWith<IOException> { negotiate(server.port, "TLSv1.3", "TLS_AES_256_GCM_SHA384") }

            // TLS 1.2 keeps the defaults of the provider
            negotiate(server.port, "TLSv1.2")
        }
    }

    @Test
    fun testBoringSslRestrictsCiphers() = ioxideTest {
        withBoringSsl {
            secure(TcpTransportOptions(tlsProvider = TlsProvider.BoringSsl, cipherList = "ECDHE-RSA-AES128-GCM-SHA256")).use { server ->
                assertEquals("TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256", negotiate(server.port, "TLSv1.2"))

                assertFailsWith<IOException> { negotiate(server.port, "TLSv1.2", "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384") }
            }

            secure(TcpTransportOptions(tlsProvider = TlsProvider.BoringSsl, cipherSuites = "TLS_CHACHA20_POLY1305_SHA256")).use { server ->
                assertEquals("TLS_CHACHA20_POLY1305_SHA256", negotiate(server.port, "TLSv1.3"))
            }
        }
    }

    @Test
    fun testUnsupportedCiphersStopTheStart() = ioxideTest {
        val tls12 = assertFailsWith<IllegalArgumentException> {
            secure(TcpTransportOptions(tlsProvider = TlsProvider.Jdk, cipherList = "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256:TLS_ECDHE_RSA_WITH_NOT_A_CIPHER"))
        }

        assertEquals("Cipher suites not supported by the TLS provider (JDK): TLS_ECDHE_RSA_WITH_NOT_A_CIPHER", tls12.message)

        val tls13 = assertFailsWith<IllegalArgumentException> {
            secure(TcpTransportOptions(tlsProvider = TlsProvider.Jdk, cipherSuites = "TLS_NOT_A_SUITE"))
        }

        assertEquals("Cipher suites not supported by the TLS provider (JDK): TLS_NOT_A_SUITE", tls13.message)

        withBoringSsl {
            assertFailsWith<IOException> { secure(TcpTransportOptions(tlsProvider = TlsProvider.BoringSsl, cipherSuites = "TLS_NOT_A_SUITE")) }
        }
    }

    // endregion

    // region Large responses

    @ParameterizedTest
    @EnumSource(TlsProvider::class, names = ["Jdk", "BoringSsl"])
    fun testLargeResponsesAreSentCompletely(provider: TlsProvider) = ioxideTest {
        withProvider(provider) {
            secure(TcpTransportOptions(tlsProvider = provider)).use { server ->
                assertLarge(server.port)
            }
        }
    }

    // endregion

    // region Client certificates

    @Test
    fun testClientCertificatesAreValidated() = ioxideTest {
        for (revocation in X509RevocationMode.entries) {
            val validator = RecordingValidator(true, revocation)

            secure(TcpTransportOptions(tlsProvider = TlsProvider.Jdk), validator).use { server ->
                val response = getWithCertificate(server.port)

                assertEquals(200, response.statusCode())
                assertEquals(Subject, response.getContent())
            }

            assertEquals(listOf(Validation), validator.calls, "Unexpected validation with $revocation")
        }
    }

    @Test
    fun testRejectedClientCertificateFailsTheHandshake() = ioxideTest {
        val validator = RecordingValidator(false, X509RevocationMode.NoCheck)

        secure(TcpTransportOptions(tlsProvider = TlsProvider.Jdk), validator).use { server ->
            assertFailsWith<IOException> { getWithCertificate(server.port) }
        }

        // the client retries the request after the server rejected its certificate
        assertTrue(validator.calls.isNotEmpty())
        assertTrue(validator.calls.all { it == Validation }, "Unexpected validation: ${validator.calls}")
    }

    // endregion

    // region Handshake timeout

    @ParameterizedTest
    @EnumSource(TlsProvider::class, names = ["Jdk", "BoringSsl"])
    fun testHandshakeTimeout(provider: TlsProvider) = ioxideTest {
        withProvider(provider) {
            // long enough for the first handshake of a slow machine
            secure(TcpTransportOptions(tlsProvider = provider, handshakeTimeoutMs = 2000)).use { server ->
                // a client that does not start the handshake
                RawClient.connect(server.port).use { client ->
                    val connected = TimeSource.Monotonic.markNow()

                    RawClient.assertClosedByServer(client, "The connection without handshake has not been closed")

                    // by the timeout, not right away
                    assertTrue(connected.elapsedNow() >= 1.seconds, "The connection has been closed after ${connected.elapsedNow()}")
                }

                // clients completing the handshake in time are served
                assertEquals("Hello World!", get(server.port).getContent())
            }
        }
    }

    // endregion

    // region Helpers

    /**
     * Runs the given checks with BoringSSL. Where its library is not available, a server that requests it explicitly
     * must not start (instead of silently using the JDK).
     */
    private suspend fun withBoringSsl(checks: suspend () -> Unit) {
        if (IoxideNatives.boringSsl) {
            checks()
        } else {
            val e = assertFailsWith<UnsupportedOperationException> { secure(TcpTransportOptions(tlsProvider = TlsProvider.BoringSsl)) }

            assertTrue(e.message!!.startsWith("BoringSSL is not available: libioxide_boringssl.so could not be loaded"), e.message)
        }
    }

    private suspend fun withProvider(provider: TlsProvider, checks: suspend () -> Unit) {
        if (provider == TlsProvider.BoringSsl) withBoringSsl(checks) else checks()
    }

    /**
     * A server with a secure endpoint (on [port]).
     */
    private class SecureServer(val runner: IoxideRunner, val port: Int) : AutoCloseable {

        override fun close() = runner.close()

    }

    /**
     * Starts a server with a secure endpoint that answers with the subject of the client certificate (if any), or
     * with [Large] data.
     */
    private suspend fun secure(options: TcpTransportOptions, validator: CertificateValidator? = null, certificates: CertificateProvider? = null): SecureServer {
        val handler = AsyncFunctionalHandler { request ->
            if (request.header.path.toString() == "/large") {
                request.respond().content(Large, ContentType.ApplicationOctetStream).build()
            } else {
                request.respond().content(request.client.certificate?.subjectX500Principal?.name ?: "Hello World!").build()
            }
        }

        val runner = IoxideRunner(handler, EngineOptions(reactor = ReactorOptions(reactorCount = 2), tcp = options))

        val port = TestHost.nextPort()

        try {
            runner.host.bind(null, port, certificates ?: CertificateProvider.from(Security.getCertificate()), certificateValidator = validator)
            runner.start()
        } catch (e: Throwable) {
            runner.close()
            throw e
        }

        return SecureServer(runner, port)
    }

    private suspend fun assertServes(provider: TlsProvider, expected: String) {
        secure(TcpTransportOptions(tlsProvider = provider)).use { server ->
            repeat(3) {
                val response = get(server.port)

                assertEquals(200, response.statusCode())
                assertEquals("Hello World!", response.getContent())
            }

            assertLogged(server, "TLS: $expected")

            // both protocols are offered by default
            negotiate(server.port, "TLSv1.2")
            negotiate(server.port, "TLSv1.3")
        }
    }

    private fun assertLogged(server: SecureServer, text: String) {
        assertTrue(server.runner.logging.messages.any { it.contains(text) }, "'$text' not found in ${server.runner.logging.messages}")
    }

    private fun assertLarge(port: Int) {
        RawClient.connectSecure(port, receiveBuffer = 8192).use { socket ->
            val input = BufferedInputStream(socket.getInputStream())

            RawClient.send(socket, RawClient.request("/large"))

            assertTrue(Large.contentEquals(RawClient.readResponse(input).body))

            // the connection is closed after the response has been sent
            RawClient.send(socket, RawClient.request("/large", headers = listOf("Connection" to "close")))

            assertTrue(Large.contentEquals(RawClient.readResponse(input).body))

            assertEquals(-1, input.read())
        }
    }

    private suspend fun get(port: Int): HttpResponse<ByteArray> {
        val request = HttpRequest.newBuilder(URI.create("https://localhost:$port/")).GET().build()

        return TestHost.getClient(ignoreSecurityErrors = true).sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).await()
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

/**
 * The native libraries the ioxide engine uses if they are available: BoringSSL (`libioxide_boringssl.so`, found like
 * the engine finds it) and the `tls` kernel module for kernel TLS.
 *
 * Tests do not skip what is not available, but check that the engine refuses to start (or falls back) as documented.
 */
object IoxideNatives {

    val boringSsl: Boolean by lazy {
        val path = System.getProperty("ioxide.boringssl") ?: System.getenv("IOXIDE_BORINGSSL")

        runCatching {
            Arena.ofConfined().use { arena ->
                if (path != null) SymbolLookup.libraryLookup(Path.of(path), arena) else SymbolLookup.libraryLookup("libioxide_boringssl.so", arena)
            }
        }.isSuccess
    }

    val kernelTls: Boolean
        get() = Files.isDirectory(Path.of("/sys/module/tls"))

}
