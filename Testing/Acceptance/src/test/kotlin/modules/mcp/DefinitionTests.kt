package org.genhttp.testing.acceptance.modules.mcp

import org.genhttp.modules.mcp.Tool
import org.genhttp.modules.mcp.Tools
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.test

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefinitionTests {

    @Test
    fun testGeneratedInputSchema() = test {
        val add = getTool("add")

        assertEquals("Adds two integers", add["description"]!!.stringValue())
        assertNull(add["title"])
        assertNull(add["annotations"])

        val schema = add["inputSchema"]!!

        assertEquals("object", schema["type"]!!.stringValue())
        assertEquals("integer", schema["properties"]!!["a"]!!["type"]!!.stringValue())
        assertEquals("The first summand", schema["properties"]!!["a"]!!["description"]!!.stringValue())
        assertEquals("The second summand", schema["properties"]!!["b"]!!["description"]!!.stringValue())
        assertEquals("""["a","b"]""", schema["required"]!!.toString())

        assertNull(add["outputSchema"])
    }

    @Test
    fun testOptionalInput() = test {
        val schema = getTool("get_forecast")["inputSchema"]!!

        assertEquals("""["city"]""", schema["required"]!!.toString())
        assertEquals("The city to forecast", schema["properties"]!!["city"]!!["description"]!!.stringValue())
        assertEquals("""["Celsius","Fahrenheit"]""", schema["properties"]!!["unit"]!!["enum"]!!.toString())

        // upstream: "Celsius" - kotlin-reflect cannot read default values, so no default keyword is generated (docs/DEVIATIONS.md)
        assertNull(schema["properties"]!!["unit"]!!["default"])
    }

    @Test
    fun testOutputSchema() = test {
        val output = getTool("get_forecast")["outputSchema"]!!

        assertEquals("object", output["type"]!!.stringValue())
        assertEquals("""["city","temperature","conditions"]""", output["required"]!!.toString())
        assertNotNull(output["properties"]!!["warning"])
        assertNotNull(output["properties"]!!["unit"])

        val alert = output["properties"]!!["alerts"]!!["items"]!!

        assertEquals("""["kind"]""", alert["required"]!!.toString())
    }

    @Test
    fun testNullableValuesAreOmitted() = test {
        TestServer.run().use { host ->
            val response = McpClient.modern(host).call("get_forecast", jsonObject("city" to "Bern"))

            val alert = response.result["structuredContent"]!!["alerts"]!![0]!! as ObjectNode

            assertEquals("wind", alert["kind"]!!.stringValue())
            assertFalse(alert.has("note"))
        }
    }

    @Test
    fun testNoOutputSchemaForNonObjects() = test {
        assertNull(getTool("list")["outputSchema"])
        assertNull(getTool("image")["outputSchema"])
        assertNull(getTool("read_header")["outputSchema"])
    }

    @Test
    fun testAnnotations() = test {
        val forecast = getTool("get_forecast")

        assertEquals("Weather forecast", forecast["title"]!!.stringValue())

        val annotations = forecast["annotations"]!!

        assertEquals("Weather forecast", annotations["title"]!!.stringValue())
        assertTrue(annotations["readOnlyHint"]!!.booleanValue())
        assertFalse(annotations["destructiveHint"]!!.booleanValue())
        assertTrue(annotations["idempotentHint"]!!.booleanValue())
        assertTrue(annotations["openWorldHint"]!!.booleanValue())
    }

    // region Function tools (annotations cannot be placed on lambdas, so they are declared as functions)

    @Tool(readOnly = [true], destructive = [false], idempotent = [true])
    fun readTool(): Int = 1

    @Tool(readOnly = [false], destructive = [false], idempotent = [false])
    fun createTool(): Int = 1

    @Tool(readOnly = [false], destructive = [true], idempotent = [true])
    fun replaceTool(): Int = 1

    @Tool("b")
    fun namedTool(): Int = 1

    // endregion

    @Test
    fun testFunctionHints() = test {
        val tools = Tools.create()
            .add("read", "Reads", this@DefinitionTests::readTool)
            .add("create", "Creates", this@DefinitionTests::createTool)
            .add("replace", "Replaces", this@DefinitionTests::replaceTool)
            .add("plain", "Has no hints") { 1 }

        TestHost.run(tools).use { host ->
            val response = McpClient.modern(host).request("tools/list")

            val hints = response.result["tools"]!!.associate { it["name"]!!.stringValue() to it["annotations"]?.toString() }

            assertEquals("""{"readOnlyHint":true,"destructiveHint":false,"idempotentHint":true}""", hints["read"])
            assertEquals("""{"readOnlyHint":false,"destructiveHint":false,"idempotentHint":false}""", hints["create"])
            assertEquals("""{"readOnlyHint":false,"destructiveHint":true,"idempotentHint":true}""", hints["replace"])
            assertNull(hints["plain"])
        }
    }

    @Test
    fun testFunctionNameViaAttributeIsRejected() {
        assertFailsWith<IllegalStateException> { Tools.create().add("a", "A tool", this::namedTool) }
    }

    @Test
    fun testEmptyInput() = test {
        val schema = getTool("list")["inputSchema"]!!

        assertEquals("object", schema["type"]!!.stringValue())
        assertFalse(schema["additionalProperties"]!!.booleanValue())

        assertNull(getTool("add")["inputSchema"]!!["additionalProperties"])
    }

    @Test
    fun testOrderIsStable() = test {
        TestServer.run().use { host ->
            val first = McpClient.modern(host).request("tools/list")
            val second = McpClient.modern(host).request("tools/list")

            assertEquals(first.result["tools"]!!.toString(), second.result["tools"]!!.toString())
        }
    }

    @Test
    fun testDuplicateNamesAreRejected() {
        assertFailsWith<IllegalStateException> { Tools.create().add("a", "A") { 1 }.add("a", "A") { 2 }.build() }
        assertFailsWith<IllegalStateException> { Tools.create().add<TestTools>().add("add", "Adds") { 1 }.build() }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "with space", "umläut", "a/b"])
    fun testInvalidNamesAreRejected(name: String) {
        assertFailsWith<IllegalStateException> { Tools.create().add(name, "A tool") { 1 } }
    }

    @Test
    fun testLongNamesAreRejected() {
        assertFailsWith<IllegalStateException> { Tools.create().add("a".repeat(129), "A tool") { 1 } }
    }

    @Test
    fun testValidNames() = test {
        TestHost.run(Tools.create().add("admin.tools-list_V2", "A tool") { "valid" }).use { host ->
            val response = McpClient.modern(host).call("admin.tools-list_V2")

            assertEquals("valid", response.text)
        }
    }

    @Test
    fun testDefaultServerInfo() = test {
        TestHost.run(Tools.create().add<TestTools>()).use { host ->
            val response = McpClient.legacy(host, null).request("initialize")

            val info = response.result["serverInfo"]!!

            assertFalse(info["name"]!!.stringValue().isNullOrEmpty())
            assertFalse(info["version"]!!.stringValue().isNullOrEmpty())
            assertNull(info["title"])
            assertNull(response.result["instructions"])
        }
    }

    private suspend fun getTool(name: String): JsonNode {
        TestServer.run().use { host ->
            val response = McpClient.modern(host).request("tools/list")

            return response.result["tools"]!!.single { it["name"]!!.stringValue() == name }
        }
    }

}
