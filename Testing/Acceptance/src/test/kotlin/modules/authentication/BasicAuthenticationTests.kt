package org.genhttp.testing.acceptance.modules.authentication

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.authentication.BasicAuthentication
import org.genhttp.modules.authentication.authentication
import org.genhttp.modules.authentication.basic.BasicAuthenticationUser
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.layouting.provider.LayoutBuilder
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.wrap
import org.genhttp.testing.getContent

import java.net.http.HttpResponse
import java.util.Base64

import kotlin.test.assertEquals

class BasicAuthenticationTests {

    @MultiEngineTest
    fun testNoUser(engine: ServerEngine) = test {
        val content = getContent().authentication(BasicAuthentication.create())

        TestHost.run(content, engine = engine).use { runner ->
            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.Unauthorized)
        }
    }

    @MultiEngineTest
    fun testValidUser(engine: ServerEngine) = test {
        val content = getContent().authentication(
            BasicAuthentication.create()
                .add("user", "password")
        )

        TestHost.run(content, engine = engine).use { runner ->
            val response = getResponse(runner, "user", "password")

            assertEquals("user", response.getContent())
        }
    }

    @MultiEngineTest
    fun testInvalidPassword(engine: ServerEngine) = test {
        val content = getContent().authentication(
            BasicAuthentication.create()
                .add("user", "password")
        )

        TestHost.run(content, engine = engine).use { runner ->
            val response = getResponse(runner, "user", "p")

            assertEquals(ResponseStatus.Unauthorized.code, response.statusCode())
        }
    }

    @MultiEngineTest
    fun testInvalidUser(engine: ServerEngine) = test {
        val content = getContent().authentication(BasicAuthentication.create())

        TestHost.run(content, engine = engine).use { runner ->
            val response = getResponse(runner, "u", "password")

            response.assertStatus(ResponseStatus.Unauthorized)
        }
    }

    @MultiEngineTest
    fun testCustomUser(engine: ServerEngine) = test {
        val content = getContent().authentication(BasicAuthentication.create({ _, _ -> BasicAuthenticationUser("my") }))

        TestHost.run(content, engine = engine).use { runner ->
            val response = getResponse(runner, "_", "_")

            assertEquals("my", response.getContent())
        }
    }

    @MultiEngineTest
    fun testNoCustomUser(engine: ServerEngine) = test {
        val content = getContent().authentication(BasicAuthentication.create({ _, _ -> null }))

        TestHost.run(content, engine = engine).use { runner ->
            val response = getResponse(runner, "_", "_")

            response.assertStatus(ResponseStatus.Unauthorized)
        }
    }

    @MultiEngineTest
    fun testOtherAuthenticationIsNotAccepted(engine: ServerEngine) = test {
        val content = getContent().authentication(BasicAuthentication.create())

        TestHost.run(content, engine = engine).use { runner ->
            val request = runner.getRequest()
            request.header("Authorization", "Bearer 123")

            val response = runner.getResponse(request)

            response.assertStatus(ResponseStatus.Unauthorized)
        }
    }

    @MultiEngineTest
    fun testNoValidBase64(engine: ServerEngine) = test {
        val content = getContent().authentication(BasicAuthentication.create())

        TestHost.run(content, engine = engine).use { runner ->
            val request = runner.getRequest()
            request.header("Authorization", "Basic 123")

            val response = runner.getResponse(request)

            response.assertStatus(ResponseStatus.Unauthorized)
        }
    }

    private suspend fun getResponse(runner: TestHost, user: String, password: String): HttpResponse<ByteArray> {
        // .NET answers a challenge with the given credentials only once and hands out the 401 response if they
        // are declined, while the JDK client keeps asking its authenticator until it gives up with an exception,
        // so the challenge is answered by hand
        val response = runner.getResponse()

        if (response.statusCode() != ResponseStatus.Unauthorized.code) {
            return response
        }

        val credentials = Base64.getEncoder().encodeToString("$user:$password".toByteArray(Charsets.UTF_8))

        val request = runner.getRequest()
        request.header("Authorization", "Basic $credentials")

        return runner.getResponse(request)
    }

    private fun getContent(): LayoutBuilder = Layout.create()
        .index(UserReturningHandler().wrap())

}
