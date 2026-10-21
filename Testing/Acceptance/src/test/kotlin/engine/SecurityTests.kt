package org.genhttp.testing.acceptance.engine

import kotlinx.coroutines.future.await

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.CertificateProvider
import org.genhttp.api.infrastructure.CertificateValidator
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.infrastructure.SslPolicyError
import org.genhttp.api.infrastructure.SslProtocol
import org.genhttp.api.infrastructure.X509RevocationMode
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.fromString
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.security.SecureUpgrade
import org.genhttp.modules.security.providers.StrictTransportPolicy
import org.genhttp.modules.security.secureUpgrade
import org.genhttp.modules.security.strictTransport
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.Security
import org.genhttp.testing.getContent
import org.genhttp.testing.getHeader

import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyStore
import java.security.cert.X509Certificate

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.time.Duration.Companion.days

class SecurityTests {

    /**
     * As a developer I would like to serve my application in a secure manner.
     */
    @MultiEngineTest
    fun testSecure(engine: ServerEngine) = test {
        runSecure({ _, sec ->
            val client = TestHost.getClient(ignoreSecurityErrors = true)

            val response = get(client, "https://localhost:$sec")

            response.assertStatus(ResponseStatus.Ok)
            assertEquals("Hello Alice!", response.getContent())
        }, engine)
    }

    /**
     * As a developer, I expect the server to redirect to a secure endpoint
     * by default.
     */
    @MultiEngineTest
    fun testDefaultRedirection(engine: ServerEngine) = test {
        runSecure({ insec, sec ->
            val client = TestHost.getClient(followRedirects = false)

            val response = get(client, "http://localhost:$insec")

            response.assertStatus(ResponseStatus.MovedPermanently)
            assertEquals("https://localhost:$sec/", response.getHeader("Location"))
        }, engine)
    }

    /**
     * As a developer, I expect HTTP requests not to be redirected if
     * upgrades are allowed but not requested by the client.
     */
    @MultiEngineTest
    fun testNoRedirectionWithAllowed(engine: ServerEngine) = test {
        runSecure({ insec, _ ->
            val client = TestHost.getClient(followRedirects = false)

            val response = get(client, "http://localhost:$insec")

            response.assertStatus(ResponseStatus.Ok)
        }, engine, SecureUpgrade.Allow)
    }

    /**
     * As I developer, I expect requests to be upgraded if requested
     * by the client.
     */
    @MultiEngineTest
    fun testRedirectionWhenRequested(engine: ServerEngine) = test {
        runSecure({ insec, sec ->
            val client = TestHost.getClient(followRedirects = false)

            val response = get(client, "http://localhost:$insec", "Upgrade-Insecure-Requests" to "1")

            response.assertStatus(ResponseStatus.TemporaryRedirect)

            assertEquals("https://localhost:$sec/", response.getHeader("Location"))
            assertEquals("Upgrade-Insecure-Requests", response.getHeader("Vary"))

            assertFalse(response.headers().firstValue("ETag").isPresent)
        }, engine, SecureUpgrade.Allow)
    }

    /**
     * As a developer, I expect the query of the original request to be
     * preserved when a request is redirected to the secure endpoint.
     */
    @MultiEngineTest
    fun testRedirectionPreservesQuery(engine: ServerEngine) = test {
        runSecure({ insec, sec ->
            val client = TestHost.getClient(followRedirects = false)

            val response = get(client, "http://localhost:$insec/some/path?a=1&b=hello%20world&flag&c=x%26y")

            response.assertStatus(ResponseStatus.MovedPermanently)
            assertEquals("https://localhost:$sec/some/path?a=1&b=hello%20world&flag&c=x%26y", response.getHeader("Location"))
        }, engine)
    }

    /**
     * As a developer, I expect the query to be preserved when the client
     * requests an upgrade to the secure endpoint.
     */
    @MultiEngineTest
    fun testRequestedRedirectionPreservesQuery(engine: ServerEngine) = test {
        runSecure({ insec, sec ->
            val client = TestHost.getClient(followRedirects = false)

            val response = get(client, "http://localhost:$insec/?q=search", "Upgrade-Insecure-Requests" to "1")

            response.assertStatus(ResponseStatus.TemporaryRedirect)
            assertEquals("https://localhost:$sec/?q=search", response.getHeader("Location"))
        }, engine, SecureUpgrade.Allow)
    }

    /**
     * As the operator of a server, I would like to issue certificates via certbot,
     * so ACME challenges must be answered via HTTP instead of being redirected.
     */
    @MultiEngineTest
    fun testAcmeChallengeNotRedirected(engine: ServerEngine) = test {
        runSecure({ insec, _ ->
            val client = TestHost.getClient(followRedirects = false)

            val response = get(client, "http://localhost:$insec/.well-known/acme-challenge/token")

            response.assertStatus(ResponseStatus.Ok)
            assertEquals("challenge-response", response.getContent())
        }, engine, SecureUpgrade.Force)
    }

    /**
     * As a developer, I expect other well-known resources to still be
     * redirected to the secure endpoint.
     */
    @MultiEngineTest
    fun testOtherWellKnownRedirected(engine: ServerEngine) = test {
        runSecure({ insec, sec ->
            val client = TestHost.getClient(followRedirects = false)

            val response = get(client, "http://localhost:$insec/.well-known/security.txt")

            response.assertStatus(ResponseStatus.MovedPermanently)
            assertEquals("https://localhost:$sec/.well-known/security.txt", response.getHeader("Location"))
        }, engine, SecureUpgrade.Force)
    }

    /**
     * As the host of a web application, I want my application to enforce strict
     * transport security, so that man-in-the-middle attacks can be avoided to some extent.
     */
    @MultiEngineTest
    fun testTransportPolicy(engine: ServerEngine) = test {
        runSecure({ insec, sec ->
            val client = TestHost.getClient(ignoreSecurityErrors = true)

            val insecureResponse = get(client, "http://localhost:$insec")

            insecureResponse.assertStatus(ResponseStatus.Ok)
            assertFalse(insecureResponse.headers().firstValue("Strict-Transport-Security").isPresent)

            val secureResponse = get(client, "https://localhost:$sec")

            secureResponse.assertStatus(ResponseStatus.Ok)
            assertEquals("max-age=31536000; includeSubDomains; preload", secureResponse.getHeader("Strict-Transport-Security"))
        }, engine, SecureUpgrade.None)
    }

    /**
     * As the operator of the server, I expect the server to resume
     * normal operation after a security error has happened.
     */
    @MultiEngineTest
    fun testSecurityError(engine: ServerEngine) = test {
        runSecure({ _, sec ->
            assertFailsWith<IOException> {
                val client = TestHost.getClient()

                get(client, "https://localhost:$sec")
            }

            val client = TestHost.getClient(ignoreSecurityErrors = true)
            val response = get(client, "https://localhost:$sec")

            response.assertStatus(ResponseStatus.Ok)
        }, engine)
    }

    /**
     * As a web developer, I can decide not to return a certificate which will
     * abort the server SSL handshake.
     */
    @MultiEngineTest
    fun testNoCertificate(engine: ServerEngine) = test {
        runSecure({ _, sec ->
            assertFailsWith<IOException> {
                val client = TestHost.getClient(ignoreSecurityErrors = false)

                get(client, "https://localhost:$sec")
            }
        }, engine, host = "myserver")
    }

    /**
     * The verdict a CertificateValidator returns has to actually decide the connection. A
     * validator that says no is the whole point of being asked, so a client it rejects must not
     * reach the handler; one that says yes must.
     */
    @MultiEngineTest
    fun testValidatorVerdictIsHonoured(engine: ServerEngine) = test {
        assertFailsWith<IOException> {
            runWithVerdict(false, engine)
        }

        val accepted = runWithVerdict(true, engine)

        accepted.assertStatus(ResponseStatus.Ok)
    }

    private suspend fun runWithVerdict(verdict: Boolean, engine: ServerEngine): HttpResponse<ByteArray> {
        val content = Layout.create().index(Content.from(Resource.fromString("Hello Alice!")))

        TestHost(Layout.create().build(), false, engine = engine).use { runner ->
            val port = TestHost.nextPort()

            val cert = Security.getCertificate()

            val provider = CertificateProvider.from(cert)

            runner.host.handler(content)
                .bind(InetAddress.getByName("0.0.0.0"), port, provider, certificateValidator = VerdictValidator(verdict))

            runner.start()

            val client = TestHost.getClient(ignoreSecurityErrors = true)

            return get(client, "https://localhost:$port")
        }
    }

    private suspend fun runSecure(logic: suspend (Int, Int) -> Unit, engine: ServerEngine, mode: SecureUpgrade? = null, host: String = "localhost") {
        val challenges = Layout.create().add("token", Content.from(Resource.fromString("challenge-response")))

        val content = Layout.create()
            .index(Content.from(Resource.fromString("Hello Alice!")))
            .add(".well-known", Layout.create().add("acme-challenge", challenges))

        TestHost(Layout.create().build(), mode == null, engine = engine).use { runner ->
            val port = TestHost.nextPort()

            val cert = Security.getCertificate()

            // Serve the name the client actually presents (localhost) through a plain provider, so every
            // engine can answer. A test that wants the handshake to abort asks for a different name,
            // leaving the picky provider with nothing to offer localhost.
            val certificates: CertificateProvider = if (host == "localhost") {
                CertificateProvider.from(cert)
            } else {
                PickyCertificateProvider(host, cert)
            }

            runner.host.handler(content)
                .bind(InetAddress.getByName("0.0.0.0"), runner.port)
                .bind(InetAddress.getByName("0.0.0.0"), port, certificates, setOf(SslProtocol.Tls12))

            if (mode != null) {
                runner.host.secureUpgrade(mode)
                runner.host.strictTransport(StrictTransportPolicy(365.days, true, true))
            }

            runner.start()

            logic(runner.port, port)
        }
    }

    /**
     * Sends a GET request to the given URL (the counterpart of HttpClient.GetAsync).
     */
    private suspend fun get(client: HttpClient, url: String, vararg headers: Pair<String, String>): HttpResponse<ByteArray> {
        val request = HttpRequest.newBuilder(URI.create(url)).GET()

        for ((name, value) in headers) {
            request.header(name, value)
        }

        return client.sendAsync(request.build(), HttpResponse.BodyHandlers.ofByteArray()).await()
    }

    // region Supporting types

    /**
     * Answers every peer the same way, so only the verdict is under test.
     */
    private class VerdictValidator(private val verdict: Boolean) : CertificateValidator {

        // False: the engine must still ask, and a client offering nothing is what it is asked about.
        override val requireCertificate: Boolean
            get() = false

        override val revocationCheck: X509RevocationMode
            get() = X509RevocationMode.NoCheck

        override fun validate(certificate: X509Certificate?, chain: List<X509Certificate>?, policyErrors: Set<SslPolicyError>): Boolean = verdict

    }

    /**
     * Answers only for the one name it was given; provide(null) and every other name return nothing, so
     * a client asking for anything else finds no certificate and the handshake is refused.
     */
    private class PickyCertificateProvider(private val host: String, private val certificate: KeyStore.PrivateKeyEntry) : CertificateProvider {

        override fun provide(host: String?): KeyStore.PrivateKeyEntry? = if (host == this.host) certificate else null

    }

    // endregion

}
