package org.genhttp.modules.mcp.provider

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.api.protocol.getEntry
import org.genhttp.modules.mcp.TextContent
import org.genhttp.modules.mcp.protocol.JsonBuffer
import org.genhttp.modules.mcp.protocol.McpMethod
import org.genhttp.modules.mcp.protocol.McpMethods
import org.genhttp.modules.mcp.protocol.McpVersions
import org.genhttp.modules.mcp.protocol.MetaKeys
import org.genhttp.modules.mcp.protocol.RequestMetadata
import org.genhttp.modules.mcp.protocol.RpcContent
import org.genhttp.modules.mcp.protocol.RpcErrors
import org.genhttp.modules.mcp.protocol.RpcMessages
import org.genhttp.modules.mcp.provider.McpLog.clientHandled
import org.genhttp.modules.mcp.provider.McpLog.notificationAccepted
import org.genhttp.modules.mcp.provider.McpLog.operationHandled
import org.genhttp.modules.mcp.provider.McpLog.requestRejected
import org.genhttp.modules.mcp.provider.McpLog.toolCrashed
import org.genhttp.modules.mcp.provider.McpLog.toolFailed
import org.genhttp.modules.mcp.provider.McpLog.toolSucceeded
import org.genhttp.modules.reflection.MethodRegistry

import org.slf4j.Logger

import tools.jackson.core.JacksonException
import tools.jackson.core.JsonGenerator
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.JsonNodeFactory

import java.nio.ByteBuffer

import kotlin.time.Duration

/**
 * Serves tools via the Streamable HTTP transport of the Model Context Protocol.
 *
 * Serves modern clients (2026-07-28) statelessly, validating the request metadata they send with every request,
 * and legacy clients (2025-03-26 to 2025-11-25) by answering their initialize handshake without creating a
 * session. Requests are always answered with a single JSON object, never with an event stream.
 */
internal class ToolsHandler(
    private val definitions: List<MethodToolDefinition>,
    private val registry: MethodRegistry,
    identity: ServerIdentity,
    private val instructions: String?,
    cacheDuration: Duration,
    private val origins: OriginPolicy,
    private val serialization: JsonMapper
) : Handler {

    private val messages = RpcMessages(identity.name, identity.version, identity.title, cacheDuration)

    private val preparation = Any()

    @Volatile
    private var catalog: ToolCatalog? = null

    @Volatile
    private var logger: Logger? = null

    // region Functionality

    override suspend fun prepare(server: Server) {
        logger = server.logging.getLogger(McpLog.Category)

        prepareCatalog(server)
    }

    private fun prepareCatalog(server: Server): ToolCatalog {
        catalog?.let { return it }

        synchronized(preparation) {
            return catalog ?: ToolCatalog.create(server, definitions, registry, serialization).also { catalog = it }
        }
    }

    override suspend fun handle(request: Request): Response? {
        val started = System.nanoTime()

        val catalog = catalog ?: prepareCatalog(request.server)

        if (request.header.method != RequestMethod.Post) {
            logRejected(request, started, ResponseStatus.MethodNotAllowed, "Only POST is supported")

            return request.respond()
                .status(ResponseStatus.MethodNotAllowed)
                .header("Allow", "POST")
                .build()
        }

        val origin = request.header.headers.getEntry(OriginHeader)

        if (origin != null && !origins.allows(origin.toString())) {
            return error(request, started, ResponseStatus.Forbidden, null, RpcErrors.InvalidRequest, "Requests from origin '$origin' are not allowed")
        }

        val body = request.getBody()
            ?: return error(request, started, ResponseStatus.BadRequest, null, RpcErrors.ParseError, "The request does not contain a JSON-RPC message")

        val content = body.asMemory()

        val document = parse(content)
            ?: return error(request, started, ResponseStatus.BadRequest, null, RpcErrors.ParseError, "The request body is not valid JSON")

        return dispatch(request, catalog, document, started)
    }

    private suspend fun dispatch(request: Request, catalog: ToolCatalog, message: JsonNode, started: Long): Response {
        if (!message.isObject) {
            return error(request, started, ResponseStatus.BadRequest, null, RpcErrors.InvalidRequest, "Expected a single JSON-RPC message object")
        }

        var id: JsonNode? = null

        val givenId = message.get("id")

        if (givenId != null) {
            if (!givenId.isString && !givenId.isNumber) {
                return error(request, started, ResponseStatus.BadRequest, null, RpcErrors.InvalidRequest, "The id of a request needs to be a string or a number")
            }

            id = givenId
        }

        val rpc = message.get("jsonrpc")

        if (rpc == null || !rpc.isString || rpc.stringValue() != "2.0") {
            return error(request, started, ResponseStatus.BadRequest, id, RpcErrors.InvalidRequest, "Expected a JSON-RPC 2.0 message")
        }

        val methodElement = message.get("method")

        if (methodElement == null || !methodElement.isString) {
            return error(request, started, ResponseStatus.BadRequest, id, RpcErrors.InvalidRequest, "Expected a request or notification with a method")
        }

        var parameters: JsonNode? = null

        val givenParameters = message.get("params")

        if (givenParameters != null) {
            if (!givenParameters.isObject) {
                return error(request, started, ResponseStatus.BadRequest, id, RpcErrors.InvalidRequest, "The parameters of a request need to be an object")
            }

            parameters = givenParameters
        }

        val (method, methodName) = McpMethods.identify(methodElement)

        if (id == null) {
            val logger = getLogger(request)

            if (logger.isDebugEnabled) {
                logger.notificationAccepted(methodName ?: methodElement.stringValue())
            }

            return request.respond()
                .status(ResponseStatus.Accepted)
                .build()
        }

        if (method == McpMethod.Initialize) {
            return initialize(request, started, id, parameters)
        }

        val header = request.header.headers.getEntry(VersionHeader)

        val version: String

        if (header != null) {
            val known = McpVersions.find(header)

            if (known == null) {
                val requested = header.toString()

                logRejected(request, started, ResponseStatus.BadRequest, "Unsupported protocol version '$requested'")

                return respond(request, ResponseStatus.BadRequest, RpcMessages.unsupportedVersion(id, requested))
            }

            version = known
        } else if (RequestMetadata.tryGetMeta(parameters)?.has(MetaKeys.ProtocolVersion) == true) {
            return error(request, started, ResponseStatus.BadRequest, id, RpcErrors.HeaderMismatch, "The MCP-Protocol-Version header is required")
        } else {
            version = McpVersions.Unspecified
        }

        val modern = McpVersions.isModern(version)

        if (modern) {
            val problem = RequestMetadata.validate(request, version, method, methodElement, parameters)

            if (problem != null) {
                return error(request, started, ResponseStatus.BadRequest, id, problem.code, problem.message)
            }
        }

        return when (method) {
            McpMethod.CallTool -> callTool(request, catalog, started, id, parameters, modern)

            McpMethod.ListTools -> listTools(request, catalog, started, id, parameters, version)

            McpMethod.Discover -> discover(request, started, id, parameters, version)

            McpMethod.Ping -> complete(request, started, "ping", RpcMessages.beginResult(id), version, cacheable = false)

            McpMethod.ListResources -> emptyList(request, started, id, "resources/list", "resources", version)

            McpMethod.ListResourceTemplates -> emptyList(request, started, id, "resources/templates/list", "resourceTemplates", version)

            McpMethod.ListPrompts -> emptyList(request, started, id, "prompts/list", "prompts", version)

            else -> error(
                request, started, if (modern) ResponseStatus.NotFound else ResponseStatus.Ok, id, RpcErrors.MethodNotFound,
                "Method '${methodElement.stringValue()}' is not supported"
            )
        }
    }

    private fun initialize(request: Request, started: Long, id: JsonNode, parameters: JsonNode?): Response {
        val version = McpVersions.negotiate(getString(parameters, "protocolVersion"))

        val buffer = RpcMessages.beginResult(id)
        val w = buffer.writer

        w.writeStringProperty("protocolVersion", version)

        w.writeName("capabilities")
        w.writeRawValue(Capabilities)

        w.writeName("serverInfo")
        w.writeRawValue(messages.serverInfo)

        writeInstructions(w)

        val response = respond(request, ResponseStatus.Ok, messages.endResult(buffer, modern = false, cacheable = false))

        logClient(request, started, "initialize", parameters?.takeIf { it.isObject }?.get("clientInfo"), version)

        return response
    }

    private fun discover(request: Request, started: Long, id: JsonNode, parameters: JsonNode?, version: String): Response {
        val buffer = RpcMessages.beginResult(id)
        val w = buffer.writer

        RpcMessages.writeSupportedVersions(w, "supportedVersions")

        w.writeName("capabilities")
        w.writeRawValue(Capabilities)

        writeInstructions(w)

        val response = respond(request, ResponseStatus.Ok, messages.endResult(buffer, McpVersions.isModern(version), cacheable = true))

        logClient(request, started, "server/discover", RequestMetadata.tryGetMeta(parameters)?.get(MetaKeys.ClientInfo), version)

        return response
    }

    private fun listTools(request: Request, catalog: ToolCatalog, started: Long, id: JsonNode, parameters: JsonNode?, version: String): Response {
        val cursor = parameters?.takeIf { it.isObject }?.get("cursor")

        if (cursor != null && !cursor.isNull) {
            return error(request, started, ResponseStatus.Ok, id, RpcErrors.InvalidParams, "The given cursor is not valid")
        }

        val buffer = RpcMessages.beginResult(id)

        buffer.writer.writeName("tools")
        buffer.writer.writeRawValue(catalog.list)

        return complete(request, started, "tools/list", buffer, version, cacheable = true)
    }

    private fun emptyList(request: Request, started: Long, id: JsonNode, method: String, property: String, version: String): Response {
        val buffer = RpcMessages.beginResult(id)

        buffer.writer.writeArrayPropertyStart(property)
        buffer.writer.writeEndArray()

        return complete(request, started, method, buffer, version, cacheable = true)
    }

    private suspend fun callTool(request: Request, catalog: ToolCatalog, started: Long, id: JsonNode, parameters: JsonNode?, modern: Boolean): Response {
        val name = parameters?.takeIf { it.isObject }?.get("name")

        if (parameters == null || name == null || !name.isString) {
            return error(request, started, ResponseStatus.Ok, id, RpcErrors.InvalidParams, "The name of the tool to call is required")
        }

        val tool = catalog.find(name.stringValue())
            ?: return error(request, started, ResponseStatus.Ok, id, RpcErrors.InvalidParams, "Unknown tool: ${name.stringValue()}")

        var arguments: JsonNode = EmptyArguments

        val givenArguments = parameters.get("arguments")

        if (givenArguments != null && !givenArguments.isNull) {
            if (!givenArguments.isObject) {
                return error(request, started, ResponseStatus.Ok, id, RpcErrors.InvalidParams, "The arguments of a tool call need to be an object")
            }

            arguments = givenArguments
        }

        val outcome = tool.invoker.invoke(arguments, ToolContext(request, this))

        val buffer = RpcMessages.beginResult(id)

        ToolResultWriter.write(buffer.writer, outcome, modern)

        val response = respond(request, ResponseStatus.Ok, messages.endResult(buffer, modern, cacheable = false))

        logOutcome(request, started, tool.name, outcome)

        return response
    }

    // endregion

    // region Helpers

    private fun complete(request: Request, started: Long, method: String, buffer: JsonBuffer, version: String, cacheable: Boolean): Response {
        val response = respond(request, ResponseStatus.Ok, messages.endResult(buffer, McpVersions.isModern(version), cacheable))

        val logger = getLogger(request)

        if (logger.isInfoEnabled) {
            logger.operationHandled(method, elapsed(started), version)
        }

        return response
    }

    private fun error(request: Request, started: Long, status: ResponseStatus, id: JsonNode?, code: Int, message: String): Response {
        logRejected(request, started, status, message)

        return respond(request, status, RpcMessages.error(id, code, message))
    }

    private fun writeInstructions(writer: JsonGenerator) {
        if (instructions != null) {
            writer.writeStringProperty("instructions", instructions)
        }
    }

    private fun getLogger(request: Request): Logger =
        logger ?: request.server.logging.getLogger(McpLog.Category).also { logger = it }

    private fun logClient(request: Request, started: Long, method: String, client: JsonNode?, version: String) {
        val logger = getLogger(request)

        if (!logger.isInfoEnabled) {
            return
        }

        val name = if (client != null && client.isObject) getString(client, "name") else null
        val clientVersion = if (client != null && client.isObject) getString(client, "version") else null

        logger.clientHandled(method, name ?: "unknown client", clientVersion ?: "", elapsed(started), version)
    }

    private fun logOutcome(request: Request, started: Long, tool: String, outcome: ToolOutcome) {
        val logger = getLogger(request)

        val crash = outcome.crash

        if (crash != null) {
            logger.toolCrashed(crash, tool, elapsed(started))
        } else if (logger.isInfoEnabled) {
            if (outcome.result.isError) {
                logger.toolFailed(tool, elapsed(started), outcome.result.content.filterIsInstance<TextContent>().firstOrNull()?.text ?: "error result")
            } else {
                logger.toolSucceeded(tool, elapsed(started))
            }
        }
    }

    private fun logRejected(request: Request, started: Long, status: ResponseStatus, reason: String) {
        val logger = getLogger(request)

        if (logger.isInfoEnabled) {
            logger.requestRejected(status.code, elapsed(started), reason)
        }
    }

    // endregion

    private companion object {

        val OriginHeader = ByteString("Origin")

        val VersionHeader = ByteString("MCP-Protocol-Version")

        val EmptyArguments: JsonNode = JsonNodeFactory.instance.objectNode()

        const val Capabilities = """{"tools":{}}"""

        fun respond(request: Request, status: ResponseStatus, content: ByteArray): Response =
            request.respond()
                .status(status)
                .content(RpcContent(content))
                .build()

        /**
         * Parses the given request body (which needs to consist of exactly one JSON value).
         */
        fun parse(content: ByteBuffer): JsonNode? {
            val bytes = ByteArray(content.remaining())

            content.duplicate().get(bytes)

            return try {
                JsonBuffer.Mapper.createParser(bytes).use { parser ->
                    if (parser.nextToken() == null) {
                        return null
                    }

                    val document = JsonBuffer.Mapper.readValue(parser, JsonNode::class.java)

                    if (parser.nextToken() != null) null else document
                }
            } catch (_: JacksonException) {
                null
            }
        }

        fun getString(parameters: JsonNode?, property: String): String? {
            val value = parameters?.takeIf { it.isObject }?.get(property)

            return if (value != null && value.isString) value.stringValue() else null
        }

        fun elapsed(started: Long): Double = (System.nanoTime() - started) / 1_000_000.0

    }

}
