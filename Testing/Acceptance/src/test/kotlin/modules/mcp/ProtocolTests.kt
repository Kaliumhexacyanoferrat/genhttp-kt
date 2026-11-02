package org.genhttp.testing.acceptance.modules.mcp

import org.genhttp.api.content.Concern
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.api.protocol.getEntry
import org.genhttp.modules.io.from
import org.genhttp.modules.mcp.Tools
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.MethodSource

import tools.jackson.databind.node.ObjectNode

import java.net.http.HttpRequest
import java.util.Base64

import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

class ProtocolTests {

    // region Legacy clients

    @MultiEngineTest
    fun testLegacyHandshake(engine: ServerEngine) = test {
        TestServer.run(engine).use { host ->
            val client = McpClient.legacy(host, null)

            val initialize = client.request(
                "initialize", jsonObject(
                    "protocolVersion" to "2025-06-18",
                    "capabilities" to jsonObject(),
                    "clientInfo" to jsonObject("name" to "claude-code", "version" to "2.0.0")
                )
            )

            assertEquals(ResponseStatus.Ok.code, initialize.status)
            assertEquals("application/json", initialize.contentType)

            val result = initialize.result

            assertEquals("2025-06-18", result["protocolVersion"]!!.stringValue())
            assertNotNull(result["capabilities"]!!["tools"])
            assertEquals("test-server", result["serverInfo"]!!["name"]!!.stringValue())
            assertEquals("Test Server", result["serverInfo"]!!["title"]!!.stringValue())
            assertEquals("1.0.0", result["serverInfo"]!!["version"]!!.stringValue())
            assertEquals("Call add to add numbers.", result["instructions"]!!.stringValue())
            assertNull(result["resultType"])

            val initialized = client.notify("notifications/initialized")

            assertEquals(ResponseStatus.Accepted.code, initialized.status)
            assertNull(initialized.body)

            val session = McpClient.legacy(host, "2025-06-18")

            val tools = session.request("tools/list")

            assertEquals(TestServer.ToolCount, tools.result["tools"]!!.size())
            assertNull(tools.result["ttlMs"])

            val call = session.call("add", jsonObject("a" to 2, "b" to 3))

            assertEquals("5", call.text)
            assertFalse(call.isError)
            assertNull(call.result["resultType"])
        }
    }

    @ParameterizedTest
    @CsvSource(
        "2025-11-25, 2025-11-25",
        "2025-03-26, 2025-03-26",
        "2024-11-05, 2025-11-25",
        "2026-07-28, 2025-11-25"
    )
    fun testVersionNegotiation(requested: String, negotiated: String) = test {
        TestServer.run().use { host ->
            val response = McpClient.legacy(host, null).request("initialize", jsonObject("protocolVersion" to requested))

            assertEquals(negotiated, response.result["protocolVersion"]!!.stringValue())
        }
    }

    @Test
    fun testInitializeWithoutParameters() = test {
        TestServer.run().use { host ->
            val response = McpClient.legacy(host, null).request("initialize")

            assertEquals("2025-11-25", response.result["protocolVersion"]!!.stringValue())
        }
    }

    @Test
    fun testLegacyWithoutVersionHeader() = test {
        TestServer.run().use { host ->
            val response = McpClient.legacy(host, null).call("read_header", configure = { r -> r.header("X-Test", "value") })

            assertEquals("value", response.text)
        }
    }

    @Test
    fun testLegacyUnknownMethodIsNotAHttpError() = test {
        TestServer.run().use { host ->
            val response = McpClient.legacy(host).request("completion/complete")

            assertEquals(ResponseStatus.Ok.code, response.status)
            assertEquals(-32601, response.errorCode)
        }
    }

    // endregion

    // region Modern clients

    @MultiEngineTest
    fun testDiscover(engine: ServerEngine) = test {
        TestServer.run(engine).use { host ->
            val response = McpClient.modern(host).request("server/discover")

            assertEquals(ResponseStatus.Ok.code, response.status)

            val result = response.result

            assertEquals("complete", result["resultType"]!!.stringValue())
            assertEquals("2026-07-28", result["supportedVersions"]!![0]!!.stringValue())
            assertContains(result["supportedVersions"]!!.values().map { it.stringValue() }, "2025-11-25")
            assertNotNull(result["capabilities"]!!["tools"])
            assertEquals("Call add to add numbers.", result["instructions"]!!.stringValue())
            assertEquals(300000L, result["ttlMs"]!!.longValue())
            assertEquals("public", result["cacheScope"]!!.stringValue())
            assertEquals("test-server", result["_meta"]!!["io.modelcontextprotocol/serverInfo"]!!["name"]!!.stringValue())
        }
    }

    @MultiEngineTest
    fun testModernFlow(engine: ServerEngine) = test {
        TestServer.run(engine).use { host ->
            val client = McpClient.modern(host)

            val tools = client.request("tools/list")

            assertEquals("complete", tools.result["resultType"]!!.stringValue())
            assertEquals(300000L, tools.result["ttlMs"]!!.longValue())
            assertTrue(tools.result["tools"]!!.any { it["name"]!!.stringValue() == "add" })

            val call = client.call("add", jsonObject("a" to 40, "b" to 2))

            assertEquals("42", call.text)
            assertNull(call.result["structuredContent"])
            assertEquals("complete", call.result["resultType"]!!.stringValue())
            assertNull(call.result["ttlMs"])
            assertEquals("test-server", call.result["_meta"]!!["io.modelcontextprotocol/serverInfo"]!!["name"]!!.stringValue())
        }
    }

    @Test
    fun testCacheDuration() = test {
        TestHost.run(Tools.create().cacheDuration(1.hours).add<TestTools>()).use { host ->
            val tools = McpClient.modern(host).request("tools/list")

            assertEquals(3600000L, tools.result["ttlMs"]!!.longValue())
        }
    }

    @Test
    fun testNegativeCacheDuration() {
        assertFailsWith<IllegalArgumentException> { Tools.create().cacheDuration((-1).seconds) }
    }

    @Test
    fun testModernUnknownMethodIsNotFound() = test {
        TestServer.run().use { host ->
            val response = McpClient.modern(host).request("prompts/get", jsonObject("name" to "x"))

            assertEquals(ResponseStatus.NotFound.code, response.status)
            assertEquals(-32601, response.errorCode)
        }
    }

    @Test
    fun testPing() = test {
        TestServer.run().use { host ->
            val legacy = McpClient.legacy(host).request("ping")

            assertEquals(0, legacy.result.size())

            val modern = McpClient.modern(host).request("ping")

            assertEquals("complete", modern.result["resultType"]!!.stringValue())
        }
    }

    @ParameterizedTest
    @CsvSource(
        "resources/list, resources",
        "resources/templates/list, resourceTemplates",
        "prompts/list, prompts"
    )
    fun testEmptyLists(method: String, property: String) = test {
        TestServer.run().use { host ->
            val response = McpClient.modern(host).request(method)

            assertEquals(0, response.result[property]!!.size())
            assertEquals(300000L, response.result["ttlMs"]!!.longValue())
        }
    }

    @Test
    fun testCursorIsRejected() = test {
        TestServer.run().use { host ->
            val response = McpClient.modern(host).request("tools/list", jsonObject("cursor" to "abc"))

            assertEquals(-32602, response.errorCode)
        }
    }

    // endregion

    // region Request metadata

    @Test
    fun testUnsupportedVersion() = test {
        TestServer.run().use { host ->
            val response = McpClient.legacy(host, "1900-01-01").request("tools/list")

            assertEquals(ResponseStatus.BadRequest.code, response.status)
            assertEquals(-32022, response.errorCode)
            assertEquals("1900-01-01", response.error["data"]!!["requested"]!!.stringValue())
            assertEquals("2026-07-28", response.error["data"]!!["supported"]!![0]!!.stringValue())
            assertNotNull(response.body!!["id"])
        }
    }

    @Test
    fun testMissingVersionHeaderForModernRequest() = test {
        TestServer.run().use { host ->
            val body = message("tools/list", jsonObject("_meta" to meta()))

            val response = McpClient.post(host, body)

            assertEquals(ResponseStatus.BadRequest.code, response.status)
            assertEquals(-32020, response.errorCode)
        }
    }

    @Test
    fun testMissingMeta() = test {
        TestServer.run().use { host ->
            val response = McpClient.post(host, message("tools/list"), modern("tools/list"))

            assertEquals(ResponseStatus.BadRequest.code, response.status)
            assertEquals(-32602, response.errorCode)
        }
    }

    @Test
    fun testMissingMetaVersion() = test {
        TestServer.run().use { host ->
            val meta = meta()
            meta.remove("io.modelcontextprotocol/protocolVersion")

            val response = McpClient.post(host, message("tools/list", jsonObject("_meta" to meta)), modern("tools/list"))

            assertEquals(ResponseStatus.BadRequest.code, response.status)
            assertEquals(-32602, response.errorCode)
        }
    }

    @Test
    fun testMissingCapabilities() = test {
        TestServer.run().use { host ->
            val meta = meta()
            meta.remove("io.modelcontextprotocol/clientCapabilities")

            val response = McpClient.post(host, message("tools/list", jsonObject("_meta" to meta)), modern("tools/list"))

            assertEquals(ResponseStatus.BadRequest.code, response.status)
            assertEquals(-32602, response.errorCode)
        }
    }

    @Test
    fun testVersionMismatch() = test {
        TestServer.run().use { host ->
            val meta = meta()
            meta.put("io.modelcontextprotocol/protocolVersion", "2025-11-25")

            val response = McpClient.post(host, message("tools/list", jsonObject("_meta" to meta)), modern("tools/list"))

            assertEquals(ResponseStatus.BadRequest.code, response.status)
            assertEquals(-32020, response.errorCode)
        }
    }

    @Test
    fun testMissingMethodHeader() = test {
        TestServer.run().use { host ->
            val response = McpClient.post(host, message("tools/list", jsonObject("_meta" to meta())), modern(null))

            assertEquals(ResponseStatus.BadRequest.code, response.status)
            assertEquals(-32020, response.errorCode)
        }
    }

    @Test
    fun testMethodMismatch() = test {
        TestServer.run().use { host ->
            val response = McpClient.post(host, message("tools/list", jsonObject("_meta" to meta())), modern("server/discover"))

            assertEquals(ResponseStatus.BadRequest.code, response.status)
            assertEquals(-32020, response.errorCode)
        }
    }

    @Test
    fun testMissingNameHeader() = test {
        TestServer.run().use { host ->
            val response = McpClient.post(host, message("tools/call", jsonObject("name" to "add", "_meta" to meta())), modern("tools/call"))

            assertEquals(ResponseStatus.BadRequest.code, response.status)
            assertEquals(-32020, response.errorCode)
        }
    }

    @Test
    fun testNameMismatch() = test {
        TestServer.run().use { host ->
            val response = McpClient.modern(host).call("add", jsonObject("a" to 1, "b" to 2)) { r ->
                r.setHeader("Mcp-Name", "list")
            }

            assertEquals(ResponseStatus.BadRequest.code, response.status)
            assertEquals(-32020, response.errorCode)
        }
    }

    @Test
    fun testEncodedName() = test {
        TestServer.run().use { host ->
            val response = McpClient.modern(host).call("add", jsonObject("a" to 1, "b" to 2)) { r ->
                r.setHeader("Mcp-Name", "=?base64?${Base64.getEncoder().encodeToString("add".toByteArray(Charsets.UTF_8))}?=")
            }

            assertEquals("3", response.text)
        }
    }

    @Test
    fun testInvalidEncodedName() = test {
        TestServer.run().use { host ->
            val response = McpClient.modern(host).call("add", jsonObject("a" to 1, "b" to 2)) { r ->
                r.setHeader("Mcp-Name", "=?base64?!!!?=")
            }

            assertEquals(-32020, response.errorCode)
        }
    }

    private fun message(method: String, parameters: ObjectNode? = null): String {
        val message = jsonObject("jsonrpc" to "2.0", "id" to "req-1", "method" to method)

        if (parameters != null) {
            message.set("params", parameters)
        }

        return message.toString()
    }

    private fun meta(): ObjectNode = jsonObject(
        "io.modelcontextprotocol/protocolVersion" to McpClient.ModernVersion,
        "io.modelcontextprotocol/clientCapabilities" to jsonObject()
    )

    private fun modern(method: String?): (HttpRequest.Builder) -> Unit = { r ->
        r.header("MCP-Protocol-Version", McpClient.ModernVersion)

        if (method != null) {
            r.header("Mcp-Method", method)
        }
    }

    // endregion

    // region Transport

    @MultiEngineTest
    fun testGetIsNotAllowed(engine: ServerEngine) = test {
        TestServer.run(engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.MethodNotAllowed)

            assertEquals("POST", response.headers().allValues("Allow").joinToString(","))
        }
    }

    @Test
    fun testForeignOriginIsRejected() = test {
        TestServer.run().use { host ->
            val response = McpClient.modern(host).request("tools/list", configure = { r -> r.header("Origin", "https://evil.example") })

            assertEquals(ResponseStatus.Forbidden.code, response.status)
            assertNull(response.body!!["id"])
        }
    }

    @Test
    fun testAllowedOrigin() = test {
        TestHost.run(Tools.create().allowOrigin("https://inspector.example").add<TestTools>()).use { host ->
            val allowed = McpClient.modern(host).request("tools/list", configure = { r -> r.header("Origin", "https://INSPECTOR.example:443") })

            assertEquals(ResponseStatus.Ok.code, allowed.status)

            val invalid = McpClient.modern(host).request("tools/list", configure = { r -> r.header("Origin", "null") })

            assertEquals(ResponseStatus.Forbidden.code, invalid.status)
        }
    }

    @Test
    fun testInvalidAllowedOrigin() {
        assertFailsWith<IllegalArgumentException> { Tools.create().allowOrigin("not an origin").build() }
    }

    @ParameterizedTest
    @MethodSource("malformedMessages")
    fun testMalformedMessages(body: String, code: Int) = test {
        TestServer.run().use { host ->
            val response = McpClient.post(host, body)

            assertEquals(ResponseStatus.BadRequest.code, response.status)
            assertEquals(code, response.errorCode)
        }
    }

    @Test
    fun testEmptyBody() = test {
        TestServer.run().use { host ->
            val request = host.getRequest("/", "POST")

            val response = host.getResponse(request)

            response.assertStatus(ResponseStatus.BadRequest)
        }
    }

    @Test
    fun testStringId() = test {
        TestServer.run().use { host ->
            val response = McpClient.post(host, """{"jsonrpc":"2.0","id":"abc","method":"ping"}""")

            assertEquals("abc", response.body!!["id"]!!.stringValue())
        }
    }

    @Test
    fun testConcern() = test {
        val concern = Concern.from { request, content ->
            if (request.header.headers.getEntry("Authorization") != "Bearer secret") {
                request.respond().status(ResponseStatus.Unauthorized).build()
            } else {
                content.handle(request)
            }
        }

        val mcp = Tools.create()
            .add<TestTools>()
            .add(concern)

        TestHost.run(mcp).use { host ->
            val denied = McpClient.modern(host).request("tools/list")

            assertEquals(ResponseStatus.Unauthorized.code, denied.status)

            val allowed = McpClient.modern(host).request("tools/list", configure = { r -> r.header("Authorization", "Bearer secret") })

            assertEquals(ResponseStatus.Ok.code, allowed.status)
        }
    }

    // endregion

    companion object {

        @JvmStatic
        fun malformedMessages(): List<Arguments> = listOf(
            Arguments.of("{ not json", -32700),
            Arguments.of("[]", -32600),
            Arguments.of("""{"jsonrpc":"2.0","id":{},"method":"ping"}""", -32600),
            Arguments.of("""{"jsonrpc":"1.0","id":1,"method":"ping"}""", -32600),
            Arguments.of("""{"id":1,"method":"ping"}""", -32600),
            Arguments.of("""{"jsonrpc":"2.0","id":1,"result":{}}""", -32600),
            Arguments.of("""{"jsonrpc":"2.0","id":1,"method":"ping","params":[]}""", -32600)
        )

    }

}
