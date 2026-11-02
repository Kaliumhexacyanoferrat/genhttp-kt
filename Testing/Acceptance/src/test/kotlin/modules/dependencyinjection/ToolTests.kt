package org.genhttp.testing.acceptance.modules.dependencyinjection

import org.genhttp.api.content.documentation.Summary
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.modules.authentication.RequireRole
import org.genhttp.modules.dependencyinjection.DependentTools
import org.genhttp.modules.mcp.Tool
import org.genhttp.modules.mcp.add
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import org.junit.jupiter.api.Test

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode

import java.net.http.HttpRequest.BodyPublishers
import java.util.concurrent.atomic.AtomicInteger

import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ToolTests {

    // region Supporting data structures

    class ServiceTools(private val awesome: AwesomeService) {

        @Tool
        @Summary("Does some work")
        fun work(another: AnotherAwesomeService, times: Int = 1): String = "${awesome.doWork()}/${another.doWork()}/$times"

    }

    class ConstructionCounter {

        private val counter = AtomicInteger()

        val count: Int
            get() = counter.get()

        fun increment() {
            counter.incrementAndGet()
        }

    }

    class CountedTools(counter: ConstructionCounter) {

        init {
            counter.increment()
        }

        @Tool
        @Summary("Requires a role nobody has")
        @RequireRole("admin")
        fun restricted(): String = "restricted"

        @Tool
        @Summary("Open to everybody")
        fun open(): String = "open"

    }

    // endregion

    // region Tests

    @MultiEngineTest
    fun testConstructorAndParameterInjection(engine: ServerEngine) = test {
        val tools = DependentTools.create()
            .add<ServiceTools>()

        DependentHost.run(tools, engine).use { host ->
            val list = send(host, "tools/list", jsonObject())

            val properties = list["result"]!!["tools"]!![0]!!["inputSchema"]!!["properties"]!! as ObjectNode

            assertEquals(1, properties.size())
            assertTrue(properties.has("times"))

            val call = send(host, "tools/call", jsonObject().put("name", "work").set("arguments", jsonObject().put("times", 2)))

            assertEquals("42/24/2", text(call))
        }
    }

    @Test
    fun testFunctionInjection() = test {
        val tools = DependentTools.create()
            .add("work", "Does some work") { service: AwesomeService -> service.doWork() }

        DependentHost.run(tools).use { host ->
            val call = send(host, "tools/call", jsonObject().put("name", "work"))

            assertEquals("42", text(call))
        }
    }

    @Test
    fun testInstancePerCall() = test {
        val counter = ConstructionCounter()

        val tools = DependentTools.create()
            .add<CountedTools>()

        DependentHost.run(tools, configure = { single { counter } }).use { host ->
            val denied = send(host, "tools/call", jsonObject().put("name", "restricted"))

            assertTrue(denied["result"]!!["isError"]!!.booleanValue())
            assertEquals(0, counter.count)

            assertEquals("open", text(send(host, "tools/call", jsonObject().put("name", "open"))))
            assertEquals("open", text(send(host, "tools/call", jsonObject().put("name", "open"))))

            assertEquals(2, counter.count)
        }
    }

    private fun text(response: JsonNode): String = response["result"]!!["content"]!![0]!!["text"]!!.stringValue()

    private suspend fun send(host: TestHost, method: String, parameters: ObjectNode): JsonNode {
        val message = jsonObject().put("jsonrpc", "2.0").put("id", 1).put("method", method).set("params", parameters)

        val request = host.getRequest("/", "POST")
            .method("POST", BodyPublishers.ofString(message.toString()))
            .header("Content-Type", "application/json; charset=utf-8")

        val response = host.getResponse(request)

        return JsonMapper().readTree(response.getContent())
    }

    private fun jsonObject(): ObjectNode = JsonNodeFactory.instance.objectNode()

    // endregion

}
