package org.genhttp.testing.acceptance.modules.functional

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.functional.*
import org.genhttp.modules.io.strings.StringContent
import org.genhttp.modules.redirects.Redirect
import org.genhttp.modules.reflection.ExecutionMode
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineFrameworkTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent
import org.genhttp.testing.getHeader

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.http.HttpRequest.BodyPublishers

import kotlin.test.assertEquals

class InlineTests {

    @MultiEngineFrameworkTest
    fun testGetRoot(engine: ServerEngine, mode: ExecutionMode) = test {
        TestHost.run(Inline.create().get { 42 }.executionMode(mode), engine = engine).use { host ->
            val response = host.getResponse()

            assertEquals("42", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testGetPath(engine: ServerEngine, mode: ExecutionMode) = test {
        TestHost.run(Inline.create().get("/blubb") { 42 }.executionMode(mode), engine = engine).use { host ->
            val response = host.getResponse("/blubb")

            assertEquals("42", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testGetQueryParam(engine: ServerEngine, mode: ExecutionMode) = test {
        TestHost.run(Inline.create().get { param: Int -> param + 1 }.executionMode(mode), engine = engine).use { host ->
            val response = host.getResponse("/?param=41")

            assertEquals("42", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testGetEmptyBooleanQueryParam(engine: ServerEngine, mode: ExecutionMode) = test {
        TestHost.run(Inline.create().get { param: Boolean -> param }.executionMode(mode), engine = engine).use { host ->
            val response = host.getResponse("/?param=")

            assertEquals("0", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testGetEmptyDoubleQueryParam(engine: ServerEngine, mode: ExecutionMode) = test {
        TestHost.run(Inline.create().get { param: Double -> param }.executionMode(mode), engine = engine).use { host ->
            val response = host.getResponse("/?param=")

            assertEquals("0", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testGetEmptyStringQueryParam(engine: ServerEngine, mode: ExecutionMode) = test {
        TestHost.run(Inline.create().get { param: String -> param }.executionMode(mode), engine = engine).use { host ->
            val response = host.getResponse("/?param=")

            assertEquals("", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testGetEmptyEnumQueryParam(engine: ServerEngine, mode: ExecutionMode) = test {
        TestHost.run(Inline.create().get { param: EnumData -> param }.executionMode(mode), engine = engine).use { host ->
            val response = host.getResponse("/?param=")

            assertEquals("One", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testGetPathParam(engine: ServerEngine, mode: ExecutionMode) = test {
        TestHost.run(Inline.create().get(":param") { param: Int -> param + 1 }.executionMode(mode), engine = engine).use { host ->
            val response = host.getResponse("/41")

            assertEquals("42", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testNotFound(engine: ServerEngine, mode: ExecutionMode) = test {
        TestHost.run(Inline.create().get { 42 }.executionMode(mode), engine = engine).use { host ->
            val response = host.getResponse("/nope")

            response.assertStatus(ResponseStatus.NotFound)
        }
    }

    @MultiEngineFrameworkTest
    fun testRaw(engine: ServerEngine, mode: ExecutionMode) = test {
        val handler = Inline.create().get { request: Request ->
            request.respond()
                .status(ResponseStatus.Ok)
                .content(StringContent("42"))
        }.executionMode(mode)

        TestHost.run(handler, engine = engine).use { host ->
            val response = host.getResponse()

            assertEquals("42", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testStream(engine: ServerEngine, mode: ExecutionMode) = test {
        TestHost.run(Inline.create().get { ByteArrayInputStream("42".toByteArray()) }.executionMode(mode), engine = engine).use { host ->
            val response = host.getResponse()

            assertEquals("42", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testJson(engine: ServerEngine, mode: ExecutionMode) = test {
        TestHost.run(Inline.create().get { MyClass("42", 42, 42.0) }.executionMode(mode), engine = engine).use { host ->
            val response = host.getResponse()

            assertEquals("{\"string\":\"42\",\"int\":42,\"double\":42}", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testPostJson(engine: ServerEngine, mode: ExecutionMode) = test {
        TestHost.run(Inline.create().post { input: MyClass -> input }.executionMode(mode), engine = engine).use { host ->
            val request = host.getRequest()
                .method("POST", BodyPublishers.ofString("{\"string\":\"42\",\"int\":42,\"double\":42}"))
                .header("Content-Type", "application/json; charset=utf-8")

            val response = host.getResponse(request)

            assertEquals("{\"string\":\"42\",\"int\":42,\"double\":42}", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testAsync(engine: ServerEngine, mode: ExecutionMode) = test {
        val handler = Inline.create().get {
            val stream = ByteArrayOutputStream()

            stream.write("42".toByteArray())

            ByteArrayInputStream(stream.toByteArray())
        }.executionMode(mode)

        TestHost.run(handler, engine = engine).use { host ->
            val response = host.getResponse()

            assertEquals("42", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testHandlerBuilder(engine: ServerEngine, mode: ExecutionMode) = test {
        val target = "https://www.google.de/"

        TestHost.run(Inline.create().get { Redirect.to(target) }.executionMode(mode), engine = engine).use { host ->
            val response = host.getResponse()

            assertEquals(target, response.getHeader("Location"))
        }
    }

    @MultiEngineFrameworkTest
    fun testHandler(engine: ServerEngine, mode: ExecutionMode) = test {
        val target = "https://www.google.de/"

        TestHost.run(Inline.create().get { parent: Handler -> Redirect.to(target).build() }.executionMode(mode), engine = engine).use { host ->
            val response = host.getResponse()

            assertEquals(target, response.getHeader("Location"))
        }
    }

    @MultiEngineFrameworkTest
    fun testNullable(engine: ServerEngine, mode: ExecutionMode) = test {
        TestHost.run(Inline.create().get { i: Int? -> i ?: 42 }.executionMode(mode), engine = engine).use { host ->
            val response = host.getResponse("/?i=43")

            assertEquals("43", response.getContent())
        }
    }

    // region Supporting data structures

    data class MyClass(val string: String, val int: Int, val double: Double)

    enum class EnumData { One, Two }

    // endregion

}
