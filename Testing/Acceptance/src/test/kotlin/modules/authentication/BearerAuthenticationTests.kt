package org.genhttp.testing.acceptance.modules.authentication

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.RSAKey
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT

import org.genhttp.api.content.ProviderException
import org.genhttp.api.content.authentication.User
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.authentication.BearerAuthentication
import org.genhttp.modules.authentication.bearer.BearerAuthenticationConcernBuilder
import org.genhttp.modules.functional.Inline
import org.genhttp.modules.functional.get
import org.genhttp.modules.layouting.Layout
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import java.net.http.HttpResponse

import kotlin.test.assertEquals

class BearerAuthenticationTests {

    private val validToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"

    @MultiEngineTest
    fun testValidToken(engine: ServerEngine) = test {
        val auth = BearerAuthentication.create()
            .allowExpired()

        val response = execute(auth, engine, validToken)

        response.assertStatus(ResponseStatus.Ok)

        assertEquals("Secured", response.getContent())
    }

    @MultiEngineTest
    fun testCustomValidator(engine: ServerEngine) = test {
        val auth = BearerAuthentication.create()
            .validation { throw ProviderException(ResponseStatus.Forbidden, "Nah") }
            .allowExpired()

        val response = execute(auth, engine, validToken)

        response.assertStatus(ResponseStatus.Forbidden)
    }

    @MultiEngineTest
    fun testCustomKeyResolver(engine: ServerEngine) = test {
        val auth = BearerAuthentication.create()
            .issuer("https://facebook.com")
            .keyResolver { throw ProviderException(ResponseStatus.Forbidden, "Nah") }
            .allowExpired()

        val response = execute(auth, engine, validToken)

        response.assertStatus(ResponseStatus.Forbidden)
    }

    @MultiEngineTest
    fun testNoUser(engine: ServerEngine) = test {
        val auth = BearerAuthentication.create()
            .userMapping { _, _ -> null }
            .allowExpired()

        val response = execute(auth, engine, validToken)

        response.assertStatus(ResponseStatus.Ok)
    }

    @MultiEngineTest
    fun testUser(engine: ServerEngine) = test {
        val auth = BearerAuthentication.create()
            .userMapping { _, _ -> MyUser("User Name") }
            .allowExpired()

        val response = execute(auth, engine, validToken)

        response.assertStatus(ResponseStatus.Ok)
    }

    @MultiEngineTest
    fun testNoToken(engine: ServerEngine) = test {
        val auth = BearerAuthentication.create()
            .allowExpired()

        val response = execute(auth, engine)

        response.assertStatus(ResponseStatus.Unauthorized)
    }

    @MultiEngineTest
    fun testMalformedToken(engine: ServerEngine) = test {
        val auth = BearerAuthentication.create()
            .allowExpired()

        val response = execute(auth, engine, "Lorem Ipsum")

        response.assertStatus(ResponseStatus.BadRequest)
    }

    @MultiEngineTest
    fun testIssuerFetchesRealSigningKeys(engine: ServerEngine) = test {
        val rsa = createKey()

        createIssuer(engine, rsa).use { issuerHost ->
            val issuer = issuerHost.getUrl()

            val auth = BearerAuthentication.create().issuer(issuer).allowExpired()

            val token = createToken(issuer, rsa)

            val response = execute(auth, engine, token)

            response.assertStatus(ResponseStatus.Ok)
        }
    }

    @MultiEngineTest
    fun testIssuerRejectsTokenSignedWithUnknownKey(engine: ServerEngine) = test {
        val rsa = createKey()
        val otherRsa = createKey()

        createIssuer(engine, rsa).use { issuerHost ->
            val issuer = issuerHost.getUrl()

            val auth = BearerAuthentication.create().issuer(issuer)

            val token = createToken(issuer, otherRsa)

            val response = execute(auth, engine, token)

            response.assertStatus(ResponseStatus.Unauthorized)
        }
    }

    @MultiEngineTest
    fun testUnreachableIssuerConfigYieldsInternalServerError(engine: ServerEngine) = test {
        // no ".well-known/openid-configuration" route configured -> the issuer 404s
        TestHost.run(Layout.create(), engine = engine).use { issuerHost ->
            val issuer = issuerHost.getUrl()

            val auth = BearerAuthentication.create().issuer(issuer).allowExpired()

            val rsa = createKey()

            val token = createToken(issuer, rsa)

            val response = execute(auth, engine, token)

            response.assertStatus(ResponseStatus.InternalServerError)
        }
    }

    private fun createKey(): RSAKey = RSAKeyGenerator(2048).keyID("test-key").generate()

    private suspend fun createIssuer(engine: ServerEngine, rsa: RSAKey): TestHost {
        val jwksJson = """{"keys":[{"kty":"RSA","use":"sig","kid":"${rsa.keyID}","n":"${rsa.modulus}","e":"${rsa.publicExponent}","alg":"RS256"}]}"""

        // the handler references the host's own port (for jwks_uri), so it's built after the
        // port is known but before the not-yet-started host is actually started
        val issuerHost = TestHost(Layout.create().build(), engine = engine)

        val configJson = """{"jwks_uri":"${issuerHost.getUrl("/jwks")}"}"""

        issuerHost.host.handler(
            Layout.create()
                .add(
                    ".well-known", Layout.create()
                        .add("openid-configuration", Inline.create().get { configJson })
                )
                .add("jwks", Inline.create().get { jwksJson })
        )

        issuerHost.start()

        return issuerHost
    }

    private fun createToken(issuer: String, rsa: RSAKey): String {
        val claims = JWTClaimsSet.Builder().issuer(issuer).build()

        val token = SignedJWT(JWSHeader(JWSAlgorithm.RS256), claims)

        token.sign(RSASSASigner(rsa))

        return token.serialize()
    }

    private suspend fun execute(builder: BearerAuthenticationConcernBuilder, engine: ServerEngine, token: String? = null): HttpResponse<ByteArray> {
        val handler = Inline.create()
            .get { "Secured" }
            .add(builder)

        TestHost.run(handler, engine = engine).use { host ->
            val request = host.getRequest()

            if (token != null) {
                request.header("Authorization", "Bearer $token")
            }

            return host.getResponse(request)
        }
    }

    // region Supporting data structures

    internal class MyUser(override val displayName: String = "") : User

    // endregion

}
