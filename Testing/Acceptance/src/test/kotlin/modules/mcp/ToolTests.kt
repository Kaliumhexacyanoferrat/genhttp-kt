package org.genhttp.testing.acceptance.modules.mcp

import org.genhttp.api.content.Concern
import org.genhttp.api.content.documentation.Summary
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.modules.io.from
import org.genhttp.modules.mcp.Tool
import org.genhttp.modules.mcp.ToolResult
import org.genhttp.modules.mcp.Tools
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

import java.util.concurrent.atomic.AtomicReference

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolTests {

    @MultiEngineTest
    fun testJsonResult(engine: ServerEngine) = test {
        TestServer.run(engine).use { host ->
            val response = McpClient.modern(host).call("get_forecast", jsonObject("city" to "Bern", "unit" to "Fahrenheit", "days" to 3))

            val structured = response.result["structuredContent"]!!

            assertEquals("Bern", structured["city"]!!.stringValue())
            assertEquals(70.7, structured["temperature"]!!.doubleValue())
            assertNull(structured["warning"])
            assertEquals("3 day(s)", structured["conditions"]!![1]!!.stringValue())

            assertEquals(structured.toString(), parseJson(response.text).toString())
            assertFalse(response.isError)
        }
    }

    @Test
    fun testDefaultArguments() = test {
        TestServer.run().use { host ->
            val response = McpClient.modern(host).call("get_forecast", jsonObject("city" to "Bern"))

            assertEquals(21.5, response.result["structuredContent"]!!["temperature"]!!.doubleValue())
        }
    }

    @Test
    fun testNumbersAsStrings() = test {
        TestServer.run().use { host ->
            val response = McpClient.modern(host).call("add", jsonObject("a" to "4", "b" to 5))

            assertEquals("9", response.text)
        }
    }

    @Test
    fun testMissingArguments() = test {
        TestServer.run().use { host ->
            val response = McpClient.modern(host).call("get_forecast")

            assertTrue(response.isError)
            assertEquals("The argument 'city' is required.", response.text)
        }
    }

    @Test
    fun testNullArguments() = test {
        TestServer.run().use { host ->
            val response = McpClient.modern(host).request("tools/call", jsonObject("name" to "list", "arguments" to null))

            assertFalse(response.isError)
        }
    }

    @Test
    fun testInvalidArguments() = test {
        TestServer.run().use { host ->
            val response = McpClient.modern(host).call("add", jsonObject("a" to "four", "b" to 5))

            assertTrue(response.isError)
            AssertX.contains("The argument 'a' does not match its schema", response.text)
        }
    }

    @Test
    fun testArgumentsNeedToBeAnObject() = test {
        TestServer.run().use { host ->
            val response = McpClient.modern(host).request("tools/call", jsonObject("name" to "add", "arguments" to jsonArray()))

            assertEquals(-32602, response.errorCode)
        }
    }

    @Test
    fun testUnknownTool() = test {
        TestServer.run().use { host ->
            val response = McpClient.modern(host).call("nope")

            assertEquals(-32602, response.errorCode)
            AssertX.contains("nope", response.error["message"]!!.stringValue())
        }
    }

    @Test
    fun testMissingToolName() = test {
        TestServer.run().use { host ->
            val response = McpClient.legacy(host).request("tools/call", jsonObject())

            assertEquals(-32602, response.errorCode)

            val noParameters = McpClient.legacy(host).request("tools/call")

            assertEquals(-32602, noParameters.errorCode)
        }
    }

    @Test
    fun testProviderException() = test {
        TestServer.run().use { host ->
            val response = McpClient.modern(host).call("fail")

            assertTrue(response.isError)
            assertEquals("The date needs to be in the future", response.text)
            assertNull(response.result["structuredContent"])
        }
    }

    @ParameterizedTest
    @CsvSource(
        "crash, Database is down",
        "crash_later, Disk is full"
    )
    fun testUnexpectedExceptions(tool: String, message: String) = test {
        TestServer.run().use { host ->
            val response = McpClient.modern(host).call(tool)

            assertTrue(response.isError)
            assertEquals("The tool failed unexpectedly: $message", response.text)
        }
    }

    @Test
    fun testMarkdown() = test {
        TestServer.run().use { host ->
            val response = McpClient.modern(host).call("markdown")

            assertEquals("# Title", response.text)
            assertNull(response.result["structuredContent"])
        }
    }

    @Test
    fun testRequestAccess() = test {
        TestServer.run().use { host ->
            val response = McpClient.modern(host).call("read_header", configure = { r -> r.header("X-Test", "hello") })

            assertEquals("hello", response.text)
            assertNull(response.result["structuredContent"])
        }
    }

    @Test
    fun testContentBlocks() = test {
        TestServer.run().use { host ->
            val response = McpClient.modern(host).call("image")

            val content = response.result["content"]!!

            assertEquals(4, content.size())

            assertEquals("A pixel", content[0]!!["text"]!!.stringValue())

            assertEquals("image", content[1]!!["type"]!!.stringValue())
            assertEquals("AQID", content[1]!!["data"]!!.stringValue())
            assertEquals("image/png", content[1]!!["mimeType"]!!.stringValue())

            assertEquals("audio", content[2]!!["type"]!!.stringValue())
            assertEquals("BAU=", content[2]!!["data"]!!.stringValue())

            assertEquals("resource_link", content[3]!!["type"]!!.stringValue())
            assertEquals("https://example.com/a.txt", content[3]!!["uri"]!!.stringValue())
            assertEquals("a.txt", content[3]!!["name"]!!.stringValue())
            assertEquals("A", content[3]!!["title"]!!.stringValue())
            assertEquals("A file", content[3]!!["description"]!!.stringValue())
            assertEquals("text/plain", content[3]!!["mimeType"]!!.stringValue())
        }
    }

    @Test
    fun testStructuredResult() = test {
        TestServer.run().use { host ->
            val response = McpClient.legacy(host).call("structured")

            assertEquals("""{"ok":true,"count":2}""", response.text)
            assertTrue(response.result["structuredContent"]!!["ok"]!!.booleanValue())
        }
    }

    @Test
    fun testArrayResult() = test {
        TestServer.run().use { host ->
            val legacy = McpClient.legacy(host).call("list")

            assertEquals("""["a","b"]""", legacy.text)
            assertNull(legacy.result["structuredContent"])

            val modern = McpClient.modern(host).call("list")

            assertEquals(2, modern.result["structuredContent"]!!.size())
        }
    }

    @Test
    fun testNonAsciiText() = test {
        TestServer.run().use { host ->
            val response = McpClient.modern(host).call("echo", jsonObject("text" to "Nägeli <ti&m>"))

            assertEquals("Nägeli <ti&m>", response.text)
        }
    }

    @Test
    fun testCustomSerialization() = test {
        // upstream: JsonSerializerDefaults.General, which writes the property names as declared in C# (PascalCase)
        val options = JsonMapper.builder()
            .addModule(KotlinModule.Builder().build())
            .propertyNamingStrategy(PropertyNamingStrategies.UPPER_CAMEL_CASE)
            .build()

        TestHost.run(Tools.create().serialization(options).add<TestTools>()).use { host ->
            val response = McpClient.modern(host).call("get_forecast", jsonObject("city" to "Bern", "unit" to 1))

            assertEquals(70.7, response.result["structuredContent"]!!["Temperature"]!!.doubleValue())
        }
    }

    @Test
    fun testUnserializableStructuredData() = test {
        TestHost.run(Tools.create().add<BrokenTools>()).use { host ->
            val response = McpClient.modern(host).call("unserializable")

            assertTrue(response.isError)
            AssertX.contains("failed unexpectedly", response.text)
        }
    }

    @Test
    fun testUnserializableOutput() = test {
        TestHost.run(Tools.create().add<BrokenTools>()).use { host ->
            val response = McpClient.modern(host).call("unserializable_output")

            assertTrue(response.isError)
        }
    }

    @MultiEngineTest
    fun testLargeResult(engine: ServerEngine) = test {
        TestHost.run(Tools.create().add<BigTools>(), engine = engine).use { host ->
            repeat(2) {
                val response = McpClient.modern(host).call("big")

                assertEquals(2_000_000, response.text.length)
            }
        }
    }

    @Test
    fun testChecksum() = test {
        val checksum = AtomicReference<Long?>(null)

        val concern = Concern.from { request, content ->
            val response = content.handle(request)

            checksum.set(response!!.content!!.calculateChecksum())

            response
        }

        TestHost.run(Tools.create().add<TestTools>().add(concern)).use { host ->
            McpClient.post(host, """{"jsonrpc":"2.0","id":1,"method":"ping"}""")

            val first = checksum.get()

            McpClient.post(host, """{"jsonrpc":"2.0","id":1,"method":"ping"}""")

            assertNotNull(first)
            assertEquals(first, checksum.get())

            McpClient.post(host, """{"jsonrpc":"2.0","id":2,"method":"ping"}""")

            assertNotEquals(first, checksum.get())
        }
    }

    @Test
    fun testErrorResult() = test {
        TestHost.run(Tools.create().add<TestTools>()).use { host ->
            val response = McpClient.modern(host).call("error_result")

            assertTrue(response.isError)
            assertEquals("Not today", response.text)
        }
    }

    class BigTools {

        @Tool
        @Summary("Returns a large text")
        fun big(): String = "x".repeat(2_000_000)

    }

    class BrokenTools {

        @Tool
        @Summary("Returns data that cannot be serialized")
        fun unserializable(): ToolResult = ToolResult.json(Cyclic())

        @Tool
        @Summary("Returns an object that cannot be serialized")
        fun unserializableOutput(): Cyclic = Cyclic()

    }

    class Cyclic {

        val self: Cyclic
            get() = this

    }

}
