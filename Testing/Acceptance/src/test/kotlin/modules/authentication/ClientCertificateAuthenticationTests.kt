package org.genhttp.testing.acceptance.modules.authentication

import kotlinx.coroutines.future.await

import org.genhttp.api.content.authentication.User
import org.genhttp.api.infrastructure.CertificateProvider
import org.genhttp.api.infrastructure.CertificateValidator
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.infrastructure.SslPolicyError
import org.genhttp.api.infrastructure.X509RevocationMode
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.authentication.ClientCertificateAuthentication
import org.genhttp.modules.authentication.clientcertificate.ClientCertificateAuthenticationBuilder
import org.genhttp.modules.authentication.clientcertificate.ClientCertificateUser
import org.genhttp.modules.authentication.getUser
import org.genhttp.modules.functional.Inline
import org.genhttp.modules.functional.get
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.Security
import org.genhttp.testing.getContent

import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate

import javax.net.ssl.KeyManager
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

import kotlin.test.assertEquals

class ClientCertificateAuthenticationTests {

    // region Supporting data structures

    class ConfigurableValidator : CertificateValidator {

        var validator: ((X509Certificate?, List<X509Certificate>?, Set<SslPolicyError>) -> Boolean)? = null

        override var requireCertificate: Boolean = false

        override var revocationCheck: X509RevocationMode = X509RevocationMode.NoCheck

        override fun validate(certificate: X509Certificate?, chain: List<X509Certificate>?, policyErrors: Set<SslPolicyError>): Boolean =
            validator?.invoke(certificate, chain, policyErrors) ?: false

    }

    // endregion

    // region Tests

    @MultiEngineTest
    fun testClientAuthentication(engine: ServerEngine) = test {
        val auth = ClientCertificateAuthentication.create()
            .authorization { _, c -> c != null }

        val validator = ConfigurableValidator().apply {
            requireCertificate = true
            revocationCheck = X509RevocationMode.NoCheck
            validator = { _, _, _ -> true }
        }

        val response = testAsync(auth, validator, true, engine)

        response.assertStatus(ResponseStatus.Ok)
    }

    @MultiEngineTest
    fun testClientAuthenticationSaysNo(engine: ServerEngine) = test {
        val auth = ClientCertificateAuthentication.create()
            .authorization { _, _ -> false }

        val validator = ConfigurableValidator().apply {
            requireCertificate = true
            revocationCheck = X509RevocationMode.NoCheck
            validator = { _, _, _ -> true }
        }

        val response = testAsync(auth, validator, true, engine)

        response.assertStatus(ResponseStatus.Forbidden)
    }

    @MultiEngineTest
    fun testAuthenticationOptional(engine: ServerEngine) = test {
        val auth = ClientCertificateAuthentication.create()
            .authorization { _, c -> c == null }

        val validator = ConfigurableValidator().apply {
            requireCertificate = false
            revocationCheck = X509RevocationMode.NoCheck
            validator = { _, _, _ -> true }
        }

        val response = testAsync(auth, validator, false, engine)

        response.assertStatus(ResponseStatus.Ok)
    }

    @MultiEngineTest
    fun testUserMapping(engine: ServerEngine) = test {
        val auth = ClientCertificateAuthentication.create()
            .userMapping { _, c -> if (c != null) ClientCertificateUser(c) else null }

        val validator = ConfigurableValidator().apply {
            requireCertificate = true
            revocationCheck = X509RevocationMode.NoCheck
            validator = { _, _, _ -> true }
        }

        val response = testAsync(auth, validator, true, engine)

        response.assertStatus(ResponseStatus.Ok)

        assertEquals("CN=genhttp.local, O=GenHTTP", response.getContent())
    }

    // endregion

    // region Helpers

    private suspend fun testAsync(auth: ClientCertificateAuthenticationBuilder, validator: CertificateValidator, sendCertificate: Boolean, engine: ServerEngine): HttpResponse<ByteArray> {
        val certificate = Security.getCertificate()

        val provider = CertificateProvider.from(certificate)

        val content = Inline.create()
            .get { request: Request -> request.getUser<User>()?.displayName ?: "No user" }
            .add(auth)

        TestHost(content.build(), engine = engine).use { runner ->
            val port = TestHost.nextPort()

            runner.host
                .handler(content)
                .bind(InetAddress.getByName("0.0.0.0"), port, provider, certificateValidator = validator)

            runner.start()

            val client = getClient(sendCertificate)

            val request = HttpRequest.newBuilder(URI.create("https://localhost:$port")).GET().build()

            return client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).await()
        }
    }

    private suspend fun getClient(sendCertificate: Boolean): HttpClient {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        var keyManagers: Array<KeyManager>? = null

        if (sendCertificate) {
            val certificate = Security.getCertificate()

            val store = KeyStore.getInstance("PKCS12")
            store.load(null, null)
            store.setKeyEntry("client", certificate.privateKey, CharArray(0), certificate.certificateChain)

            val factory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            factory.init(store, CharArray(0))

            keyManagers = factory.keyManagers
        }

        val context = SSLContext.getInstance("TLS")
        context.init(keyManagers, arrayOf(trustAll), SecureRandom())

        return HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .sslContext(context)
            .build()
    }

    // endregion

}
