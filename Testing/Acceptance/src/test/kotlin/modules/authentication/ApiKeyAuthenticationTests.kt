package org.genhttp.testing.acceptance.modules.authentication

import org.genhttp.api.content.authentication.User
import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.api.protocol.getEntry
import org.genhttp.modules.authentication.ApiKeyAuthentication
import org.genhttp.modules.authentication.apikey.ApiKeyConcernBuilder
import org.genhttp.modules.authentication.apikey.ApiKeyUser
import org.genhttp.modules.authentication.authentication
import org.genhttp.modules.functional.Inline
import org.genhttp.modules.functional.get
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.fromString
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.layouting.provider.LayoutBuilder
import org.genhttp.modules.reflection.Injection
import org.genhttp.modules.reflection.injectors.UserInjector
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import kotlin.test.assertEquals

class ApiKeyAuthenticationTests {

    @MultiEngineTest
    fun testNoKey(engine: ServerEngine) = test {
        getRunnerWithKeys(engine, "123").use { runner ->
            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.Unauthorized)
        }
    }

    @MultiEngineTest
    fun testInvalidKey(engine: ServerEngine) = test {
        getRunnerWithKeys(engine, "123").use { runner ->
            val request = runner.getRequest()
            request.header("X-API-Key", "124")

            val response = runner.getResponse(request)

            assertEquals(ResponseStatus.Forbidden.code, response.statusCode())
        }
    }

    @MultiEngineTest
    fun testValidKey(engine: ServerEngine) = test {
        getRunnerWithKeys(engine, "123").use { runner ->
            val request = runner.getRequest()
            request.header("X-API-Key", "123")

            val response = runner.getResponse(request)

            response.assertStatus(ResponseStatus.Ok)
        }
    }

    @MultiEngineTest
    fun testValidKeyFromQuery(engine: ServerEngine) = test {
        val auth = ApiKeyAuthentication.create()
            .withQueryParameter("key")
            .keys("123")

        getRunnerWithAuth(auth, engine).use { runner ->
            val response = runner.getResponse("/?key=123")

            assertEquals(ResponseStatus.Ok.code, response.statusCode())
        }
    }

    @MultiEngineTest
    fun testValidKeyFromHeader(engine: ServerEngine) = test {
        val auth = ApiKeyAuthentication.create()
            .withHeader("key")
            .keys("123")

        getRunnerWithAuth(auth, engine).use { runner ->
            val request = runner.getRequest()
            request.header("key", "123")

            val response = runner.getResponse(request)

            response.assertStatus(ResponseStatus.Ok)
        }
    }

    @MultiEngineTest
    fun testCustomExtractor(engine: ServerEngine) = test {
        val auth = ApiKeyAuthentication.create()
            .extractor { r -> r.header.headers.getEntry("User-Agent") }
            .keys("123")

        getRunnerWithAuth(auth, engine).use { runner ->
            val request = runner.getRequest()
            request.header("User-Agent", "123")

            val response = runner.getResponse(request)

            response.assertStatus(ResponseStatus.Ok)
        }
    }

    @MultiEngineTest
    fun testCustomAuthenticator(engine: ServerEngine) = test {
        val authenticator: suspend (Request, String) -> User? = { _, k -> if (k.length == 5) ApiKeyUser(k) else null }

        val auth = ApiKeyAuthentication.create()
            .authenticator(authenticator)

        getRunnerWithAuth(auth, engine).use { runner ->
            val request = runner.getRequest()
            request.header("X-API-Key", "12345")

            val response = runner.getResponse(request)

            response.assertStatus(ResponseStatus.Ok)
        }
    }

    @MultiEngineTest
    fun testGetUser(engine: ServerEngine) = test {
        val auth = ApiKeyAuthentication.create()
            .keys("123")

        val injection = Injection.default()
            .add(UserInjector(ApiKeyUser::class))

        val app = Inline.create()
            .get { u: ApiKeyUser -> "${u.key}|${u.displayName}|${u.roles.size}" }
            .injectors(injection)
            .add(auth)

        TestHost.run(app, engine = engine).use { runner ->
            val request = runner.getRequest()
            request.header("X-API-Key", "123")

            val response = runner.getResponse(request)

            assertEquals("123|123|0", response.getContent())
        }
    }

    private suspend fun getRunnerWithKeys(engine: ServerEngine, vararg keys: String): TestHost {
        val auth = ApiKeyAuthentication.create()
            .keys(*keys)

        return getRunnerWithAuth(auth, engine)
    }

    private suspend fun getRunnerWithAuth(auth: ApiKeyConcernBuilder, engine: ServerEngine): TestHost {
        val content = getContent().authentication(auth)

        return TestHost.run(content, engine = engine)
    }

    private fun getContent(): LayoutBuilder = Layout.create().add(Content.from(Resource.fromString("Hello World!")))

}
