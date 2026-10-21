package org.genhttp.testing.acceptance.engine

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.layouting.Layout
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getHeader

import java.net.http.HttpResponse

import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BasicTests {

    @MultiEngineTest
    fun testLegacyHttp(engine: ServerEngine) = test {
        TestHost.run(Layout.create(), engine = engine).use { runner ->
            // the JDK client cannot speak HTTP/1.0 (upstream does not send the request with this client either)
            TestHost.getClient().use {
                val response = runner.getResponse()

                response.assertStatus(ResponseStatus.NotFound)
            }
        }
    }

    @MultiEngineTest
    fun testConnectionClose(engine: ServerEngine) = test {
        TestHost.run(Layout.create(), engine = engine).use { runner ->
            val request = runner.getRequest()
            request.header("Connection", "close")

            val response = runner.getResponse(request)

            response.assertStatus(ResponseStatus.NotFound)
            assertTrue(response.connectionTokens().any { it.equals("Close", ignoreCase = true) })
        }
    }

    @MultiEngineTest
    fun testEmptyQuery(engine: ServerEngine) = test {
        TestHost.run(Layout.create(), engine = engine).use { runner ->
            val response = runner.getResponse("/?")

            response.assertStatus(ResponseStatus.NotFound)
        }
    }

    @MultiEngineTest
    fun testNoKeepAliveHeaderOn11(engine: ServerEngine) = test {
        TestHost.run(Layout.create(), engine = engine).use { runner ->
            val response = runner.getResponse()

            assertFalse(response.connectionTokens().any { it.equals("Keep-Alive", ignoreCase = true) })
        }
    }

    @MultiEngineTest
    fun testServerHeader(engine: ServerEngine) = test {
        TestHost.run(Layout.create(), engine = engine).use { runner ->
            val response = runner.getResponse()

            // Not an exact prefix - engines identify themselves differently, so just check that the
            // response identifies itself as a GenHTTP server somewhere in the value.
            val server = response.getHeader("Server")
            assertTrue(server?.contains("genhttp", ignoreCase = true) ?: false, "Server header '$server' does not identify a GenHTTP server")
        }
    }

    /**
     * The tokens of the "Connection" header(s) of the response.
     */
    private fun HttpResponse<*>.connectionTokens(): List<String> =
        headers().allValues("Connection").flatMap { it.split(',') }.map { it.trim() }

}
