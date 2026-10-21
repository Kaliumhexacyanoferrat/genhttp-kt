package org.genhttp.testing.acceptance.modules.security

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.fromString
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.security.CorsPolicy
import org.genhttp.modules.security.cors.CorsPolicyBuilder
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent
import org.genhttp.testing.getHeader

import java.net.http.HttpResponse

import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CorsTests {

    @MultiEngineTest
    fun testPreflight(engine: ServerEngine) = test {
        getRunner(CorsPolicy.permissive(), engine).use { runner ->
            val request = runner.getRequest("/t", "OPTIONS")

            val response = runner.getResponse(request)

            response.assertStatus(ResponseStatus.NoContent)
        }
    }

    @MultiEngineTest
    fun testPermissive(engine: ServerEngine) = test {
        getRunner(CorsPolicy.permissive(), engine).use { runner ->
            val response = runner.getResponse("/t")

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("*", response.getHeader("Access-Control-Allow-Origin"))

            assertEquals("*", response.getHeader("Access-Control-Allow-Methods"))
            assertEquals("*, Authorization", response.getHeader("Access-Control-Allow-Headers"))
            assertEquals("*", response.getHeader("Access-Control-Expose-Headers"))

            assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"))

            assertEquals("86400", response.getHeader("Access-Control-Max-Age"))

            assertEquals("Hello World", response.getContent())
        }
    }

    @MultiEngineTest
    fun testPermissiveWithoutDefaultAuthorizationHeader(engine: ServerEngine) = test {
        getRunner(CorsPolicy.permissive(false), engine).use { runner ->
            val response = runner.getResponse("/t")

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("*", response.getHeader("Access-Control-Allow-Origin"))

            assertEquals("*", response.getHeader("Access-Control-Allow-Methods"))
            assertEquals("*", response.getHeader("Access-Control-Allow-Headers"))
            assertEquals("*", response.getHeader("Access-Control-Expose-Headers"))

            assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"))

            assertEquals("86400", response.getHeader("Access-Control-Max-Age"))

            assertEquals("Hello World", response.getContent())
        }
    }

    @MultiEngineTest
    fun testRestrictive(engine: ServerEngine) = test {
        getRunner(CorsPolicy.restrictive(), engine).use { runner ->
            val response = runner.getResponse("/t")

            response.assertStatus(ResponseStatus.Ok)

            assertFalse(response.hasHeader("Access-Control-Allow-Origin"))

            assertFalse(response.hasHeader("Access-Control-Allow-Methods"))
            assertFalse(response.hasHeader("Access-Control-Allow-Headers"))
            assertFalse(response.hasHeader("Access-Control-Expose-Headers"))

            assertFalse(response.hasHeader("Access-Control-Allow-Credentials"))

            assertFalse(response.hasHeader("Access-Control-Max-Age"))
        }
    }

    @MultiEngineTest
    fun testCustom(engine: ServerEngine) = test {
        val policy = CorsPolicy.restrictive()
            .add("http://google.de", listOf(RequestMethod.Get), null, listOf("Accept"), false)

        getRunner(policy, engine).use { runner ->
            val request = runner.getRequest("/t")
            request.header("Origin", "http://google.de")

            val response = runner.getResponse(request)

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("http://google.de", response.getHeader("Access-Control-Allow-Origin"))

            assertEquals("GET", response.getHeader("Access-Control-Allow-Methods"))

            assertEquals("Accept", response.getHeader("Access-Control-Expose-Headers"))

            assertEquals("Origin", response.getHeader("Vary"))
        }
    }

    private suspend fun getRunner(policy: CorsPolicyBuilder, engine: ServerEngine): TestHost {
        val handler = Layout.create()
            .add("t", Content.from(Resource.fromString("Hello World")))
            .add(policy)

        return TestHost.run(handler, engine = engine)
    }

    private fun HttpResponse<*>.hasHeader(name: String): Boolean = headers().firstValue(name).isPresent

}
