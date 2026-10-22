package org.genhttp.testing.acceptance.modules.reflection

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.functional.*
import org.genhttp.modules.reflection.ExecutionMode
import org.genhttp.modules.reflection.FromBody
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineFrameworkTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse

import kotlin.test.assertEquals

class ParameterTests {

    // region Handlers (lambdas cannot carry parameter annotations, so they are declared as functions)

    fun bodyString(@FromBody body: String): String = body

    fun bodyNumber(@FromBody number: Int): Int = number

    fun bodyBracketed(@FromBody q: String): String = "[$q]"

    // endregion

    // region Tests

    @MultiEngineFrameworkTest
    fun testCanReadSimpleTypesFromBody(engine: ServerEngine, mode: ExecutionMode) = test {
        val inline = Inline.create()
            .post(this@ParameterTests::bodyString)
            .executionMode(mode)

        TestHost.run(inline, engine = engine).use { runner ->
            val response = post(runner, "1")

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("1", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testConversionError(engine: ServerEngine, mode: ExecutionMode) = test {
        val inline = Inline.create()
            .post(this@ParameterTests::bodyNumber)
            .executionMode(mode)

        TestHost.run(inline, engine = engine).use { runner ->
            val response = post(runner, "ABC")

            response.assertStatus(ResponseStatus.BadRequest)
        }
    }

    @MultiEngineFrameworkTest
    fun testQueryStringIsDecoded(engine: ServerEngine, mode: ExecutionMode) = test {
        val inline = Inline.create()
            .get { q: String -> "[$q]" }
            .executionMode(mode)

        TestHost.run(inline, engine = engine).use { runner ->
            assertContent(runner, "/?q=a%20b", "[a b]")
            assertContent(runner, "/?q=a+b", "[a b]")
            assertContent(runner, "/?q=%C3%A4", "[ä]")
            assertContent(runner, "/?q=100%25", "[100%]")
            assertContent(runner, "/?q=plain", "[plain]")
        }
    }

    @MultiEngineFrameworkTest
    fun testQueryNumberIsDecoded(engine: ServerEngine, mode: ExecutionMode) = test {
        val inline = Inline.create()
            .get { n: Int -> n }
            .executionMode(mode)

        TestHost.run(inline, engine = engine).use { runner ->
            assertContent(runner, "/?n=%2B42", "42")
        }
    }

    @MultiEngineFrameworkTest
    fun testPathStringIsDecoded(engine: ServerEngine, mode: ExecutionMode) = test {
        val inline = Inline.create()
            .get("/:name/") { name: String -> "[$name]" }
            .executionMode(mode)

        TestHost.run(inline, engine = engine).use { runner ->
            assertContent(runner, "/a%20b/", "[a b]")
            assertContent(runner, "/a+b/", "[a+b]")
            assertContent(runner, "/%C3%A4/", "[ä]")
            assertContent(runner, "/plain/", "[plain]")
        }
    }

    @MultiEngineFrameworkTest
    fun testBodyStringIsNotDecoded(engine: ServerEngine, mode: ExecutionMode) = test {
        val inline = Inline.create()
            .post(this@ParameterTests::bodyBracketed)
            .executionMode(mode)

        TestHost.run(inline, engine = engine).use { runner ->
            val request = runner.getRequest()
                .method("POST", BodyPublishers.ofString("a%20b+ä"))
                .header("Content-Type", "text/plain; charset=utf-8")

            val response = runner.getResponse(request)

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("[a%20b+ä]", response.getContent())
        }
    }

    private suspend fun assertContent(host: TestHost, path: String, expected: String) {
        val response = host.getResponse(path)

        response.assertStatus(ResponseStatus.Ok)

        assertEquals(expected, response.getContent())
    }

    private suspend fun post(host: TestHost, body: String): HttpResponse<ByteArray> {
        val request = host.getRequest()
            .method("POST", BodyPublishers.ofString(body))
            .header("Content-Type", "text/plain; charset=utf-8")

        return host.getResponse(request)
    }

    // endregion

}
