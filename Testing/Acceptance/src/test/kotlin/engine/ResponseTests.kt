package org.genhttp.testing.acceptance.engine

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.io.strings.StringContent
import org.genhttp.modules.layouting.Layout
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.wrap
import org.genhttp.testing.getContent
import org.genhttp.testing.getContentHeader
import org.genhttp.testing.getHeader

import java.time.Instant
import java.time.temporal.ChronoUnit

import kotlin.test.assertEquals

class ResponseTests {

    /**
     * As a developer, I'd like to use all of the response builders methods.
     */
    @MultiEngineTest
    fun testProperties(engine: ServerEngine) = test {
        val provider = ResponseProvider()

        val router = Layout.create().index(provider.wrap())

        TestHost.run(router, engine = engine).use { runner ->
            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("Hello World", response.getContent())
            assertEquals("text/x-custom", response.getContentHeader("Content-Type"))

            assertEquals("Test Runner", response.getHeader("X-Powered-By"))
        }
    }

    /**
     * As a client, I'd like a response containing an empty body to return a Content-Length of 0.
     */
    @MultiEngineTest
    fun testEmptyBody(engine: ServerEngine) = test {
        val provider = ResponseProvider()

        val router = Layout.create().index(provider.wrap())

        TestHost.run(router, engine = engine).use { runner ->
            val request = runner.getRequest(method = "POST")

            val response = runner.getResponse(request)

            AssertX.isNullOrEmpty(response.getContentHeader("Content-Type"))

            assertEquals("0", response.getContentHeader("Content-Length"))
        }
    }

    private class ResponseProvider : Handler {

        val modified: Instant = Instant.now().minus(10, ChronoUnit.DAYS)

        override suspend fun prepare(server: Server) {}

        override suspend fun handle(request: Request): Response? {
            if (request.header.method == RequestMethod.Post) {
                return request.respond()
                    .content(StringContent("", ContentType("")))
                    .build()
            }

            return request.respond()
                .content(StringContent("Hello World", ContentType("text/x-custom")))
                .header("X-Powered-By", "Test Runner")
                .build()
        }

    }

}
