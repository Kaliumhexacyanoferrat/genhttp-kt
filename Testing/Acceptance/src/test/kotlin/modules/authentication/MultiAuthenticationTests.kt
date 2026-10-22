package org.genhttp.testing.acceptance.modules.authentication

import kotlinx.coroutines.runBlocking

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.BuilderMissingPropertyException
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.authentication.ApiKeyAuthentication
import org.genhttp.modules.authentication.BasicAuthentication
import org.genhttp.modules.authentication.BearerAuthentication
import org.genhttp.modules.authentication.ClientCertificateAuthentication
import org.genhttp.modules.authentication.MultiAuthentication
import org.genhttp.modules.authentication.multi.MultiAuthenticationConcern
import org.genhttp.modules.authentication.multi.MultiAuthenticationConcernBuilder
import org.genhttp.modules.functional.Inline
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent
import org.genhttp.api.infrastructure.ServerEngine

import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.Base64

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class MultiAuthenticationTests {

    // region Supporting data structures

    private val validBearerToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"

    private class MockHandler : Handler {

        override suspend fun handle(request: Request): Response? {
            throw NotImplementedError()
        }

        override suspend fun prepare(server: Server) {
            throw NotImplementedError()
        }

    }

    // endregion

    @Test
    fun testConcernFullBuild() {
        val builder = MultiAuthentication
            .create()
            .add(BearerAuthentication.create())
            .add(ApiKeyAuthentication.create().authenticator { _, _ -> null })
            .add(BasicAuthentication.create())
            .add(BasicAuthentication.create({ _, _ -> null }))
            .add(ClientCertificateAuthentication.create())

        val concern = builder.build(MockHandler())

        assertIs<MultiAuthenticationConcern>(concern)
    }

    @MultiEngineTest
    fun testSingleBearerPass(engine: ServerEngine) = test {
        val builder = MultiAuthentication
            .create()
            .add(BearerAuthentication.create().allowExpired())

        val response = execute(builder, engine) { request ->
            request.header("Authorization", "Bearer $validBearerToken")
        }

        response.assertStatus(ResponseStatus.Ok)

        assertEquals("Secured", response.getContent())
    }

    @MultiEngineTest
    fun testSingleBearerFail(engine: ServerEngine) = test {
        val builder = MultiAuthentication
            .create()
            .add(BearerAuthentication.create().allowExpired())

        val response = execute(builder, engine)

        response.assertStatus(ResponseStatus.Unauthorized)
    }

    @MultiEngineTest
    fun testSingleBasicPass(engine: ServerEngine) = test {
        val builder = MultiAuthentication
            .create()
            .add(BasicAuthentication.create().add("user", "pass"))

        val response = execute(builder, engine) { request ->
            request.header("Authorization", "Basic " + Base64.getEncoder().encodeToString("user:pass".toByteArray(Charsets.UTF_8)))
        }

        response.assertStatus(ResponseStatus.Ok)

        assertEquals("Secured", response.getContent())
    }

    @MultiEngineTest
    fun testSingleBasicFail(engine: ServerEngine) = test {
        val builder = MultiAuthentication
            .create()
            .add(BasicAuthentication.create().add("user", "pass"))

        val response = execute(builder, engine) { request ->
            request.header("Authorization", "Basic " + Base64.getEncoder().encodeToString("user:invalidpass".toByteArray(Charsets.UTF_8)))
        }

        response.assertStatus(ResponseStatus.Unauthorized)
        assertBasicChallenge(response)
    }

    @MultiEngineTest
    fun testCombinedFirstPass(engine: ServerEngine) = test {
        val builder = MultiAuthentication
            .create()
            .add(BearerAuthentication.create().allowExpired())
            .add(BasicAuthentication.create().add("user", "pass"))

        val response = execute(builder, engine) { request ->
            request.header("Authorization", "Bearer $validBearerToken")
        }

        response.assertStatus(ResponseStatus.Ok)

        assertEquals("Secured", response.getContent())
    }

    @MultiEngineTest
    fun testCombinedSecondPass(engine: ServerEngine) = test {
        val builder = MultiAuthentication
            .create()
            .add(BearerAuthentication.create().allowExpired())
            .add(BasicAuthentication.create().add("user", "pass"))

        val response = execute(builder, engine) { request ->
            request.header("Authorization", "Basic " + Base64.getEncoder().encodeToString("user:pass".toByteArray(Charsets.UTF_8)))
        }

        response.assertStatus(ResponseStatus.Ok)

        assertEquals("Secured", response.getContent())
    }

    @MultiEngineTest
    fun testCombinedFail(engine: ServerEngine) = test {
        val builder = MultiAuthentication
            .create()
            .add(BearerAuthentication.create().allowExpired())
            .add(BasicAuthentication.create().add("user", "pass"))

        val response = execute(builder, engine) { request ->
            request.header("Authorization", "Basic " + Base64.getEncoder().encodeToString("user:invalidpass".toByteArray(Charsets.UTF_8)))
        }

        response.assertStatus(ResponseStatus.Unauthorized)
        assertBasicChallenge(response)
    }

    @MultiEngineTest
    fun testEmpty(engine: ServerEngine) {
        val builder = MultiAuthentication.create()

        assertFailsWith<BuilderMissingPropertyException> {
            runBlocking { execute(builder, engine) }
        }
    }

    private fun assertBasicChallenge(response: HttpResponse<ByteArray>) {
        val challenge = response.headers().allValues("WWW-Authenticate").firstOrNull { value ->
            val scheme = value.substringBefore(' ')
            val parameter = value.substringAfter(' ', "")

            scheme == "Basic" && parameter.startsWith("realm")
        }

        assertNotNull(challenge)
    }

    private suspend fun execute(builder: MultiAuthenticationConcernBuilder, engine: ServerEngine, authAction: ((HttpRequest.Builder) -> Unit)? = null): HttpResponse<ByteArray> {
        val handler = Inline.create()
            .get { "Secured" }
            .add(builder)

        TestHost.run(handler, engine = engine).use { host ->
            val request = host.getRequest()

            authAction?.invoke(request)

            return host.getResponse(request)
        }
    }

}
