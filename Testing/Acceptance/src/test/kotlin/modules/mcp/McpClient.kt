package org.genhttp.testing.acceptance.modules.mcp

import org.genhttp.testing.TestHost
import org.genhttp.testing.getContent

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode

import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.util.concurrent.atomic.AtomicInteger

import kotlin.test.fail

/**
 * Sends MCP messages the way a modern or a legacy client would.
 */
internal class McpClient private constructor(private val host: TestHost, private val version: String?, private val modern: Boolean) {

    suspend fun request(method: String, parameters: ObjectNode? = null, configure: ((HttpRequest.Builder) -> Unit)? = null): McpResponse {
        val message = jsonObject(
            "jsonrpc" to "2.0",
            "id" to nextId.incrementAndGet(),
            "method" to method
        )

        return send(message, method, parameters, configure)
    }

    suspend fun notify(method: String): McpResponse {
        val message = jsonObject(
            "jsonrpc" to "2.0",
            "method" to method
        )

        return send(message, method, null, null)
    }

    suspend fun call(tool: String, arguments: ObjectNode? = null, configure: ((HttpRequest.Builder) -> Unit)? = null): McpResponse {
        val parameters = jsonObject("name" to tool)

        if (arguments != null) {
            parameters.set("arguments", arguments)
        }

        return request("tools/call", parameters, configure)
    }

    private suspend fun send(message: ObjectNode, method: String, parameters: ObjectNode?, configure: ((HttpRequest.Builder) -> Unit)?): McpResponse {
        val params = if (modern) {
            (parameters ?: jsonObject()).set(
                "_meta", jsonObject(
                    "io.modelcontextprotocol/protocolVersion" to version,
                    "io.modelcontextprotocol/clientInfo" to jsonObject("name" to "test-client", "version" to "1.2.3"),
                    "io.modelcontextprotocol/clientCapabilities" to jsonObject()
                )
            )
        } else {
            parameters
        }

        if (params != null) {
            message.set("params", params)
        }

        return post(host, message.toString()) { request ->
            if (version != null) {
                request.header("MCP-Protocol-Version", version)
            }

            if (modern) {
                request.header("Mcp-Method", method)

                val name = params?.get("name")

                if (name != null && name.isString) {
                    request.header("Mcp-Name", name.stringValue())
                }
            }

            configure?.invoke(request)
        }
    }

    companion object {

        const val ModernVersion = "2026-07-28"

        private val nextId = AtomicInteger()

        fun modern(host: TestHost): McpClient = McpClient(host, ModernVersion, true)

        fun legacy(host: TestHost, version: String? = "2025-06-18"): McpClient = McpClient(host, version, false)

        suspend fun post(host: TestHost, body: String, configure: ((HttpRequest.Builder) -> Unit)? = null): McpResponse {
            val request = host.getRequest("/", "POST")
                .method("POST", BodyPublishers.ofString(body))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json, text/event-stream")

            configure?.invoke(request)

            val response = host.getResponse(request)

            val content = response.getContent()

            val mediaType = response.headers().firstValue("Content-Type").orElse(null)?.substringBefore(';')?.trim()

            return McpResponse(response.statusCode(), if (content.isNotEmpty()) parseJson(content) else null, mediaType)
        }

    }

}

/**
 * The response to an MCP message (HTTP status, the parsed JSON-RPC message and the media type of the response).
 */
internal data class McpResponse(val status: Int, val body: JsonNode?, val contentType: String?) {

    val result: ObjectNode
        get() = body?.get("result") as? ObjectNode ?: fail("Expected a result, but got: $body")

    val error: ObjectNode
        get() = body?.get("error") as? ObjectNode ?: fail("Expected an error, but got: $body")

    val errorCode: Int
        get() = error["code"]!!.intValue()

    val text: String
        get() = result["content"]!![0]!!["text"]!!.stringValue()

    val isError: Boolean
        get() = result["isError"]!!.booleanValue()

}

private val Nodes: JsonNodeFactory = JsonNodeFactory.instance

private val Mapper: JsonMapper = JsonMapper()

/**
 * Creates a JSON object with the given members (`new JsonObject { ["a"] = 1 }` upstream).
 *
 * Values may be JSON nodes, strings, integers, doubles, booleans or `null` (a JSON null).
 */
internal fun jsonObject(vararg members: Pair<String, Any?>): ObjectNode {
    val node = Nodes.objectNode()

    for ((name, value) in members) {
        node.set(name, toNode(value))
    }

    return node
}

/**
 * Creates a JSON array with the given items (`new JsonArray(...)` upstream).
 */
internal fun jsonArray(vararg items: Any?): ArrayNode {
    val node = Nodes.arrayNode()

    for (item in items) {
        node.add(toNode(item))
    }

    return node
}

/**
 * Parses the given JSON (`JsonNode.Parse(...)` upstream).
 */
internal fun parseJson(content: String): JsonNode = Mapper.readTree(content)

private fun toNode(value: Any?): JsonNode = when (value) {
    null -> Nodes.nullNode()
    is JsonNode -> value
    is String -> Nodes.stringNode(value)
    is Int -> Nodes.numberNode(value)
    is Double -> Nodes.numberNode(value)
    is Boolean -> Nodes.booleanNode(value)
    else -> throw IllegalArgumentException("Unsupported JSON value: $value")
}
