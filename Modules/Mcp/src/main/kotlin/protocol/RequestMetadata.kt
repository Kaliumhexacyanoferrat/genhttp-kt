package org.genhttp.modules.mcp.protocol

import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.getEntry

import tools.jackson.databind.JsonNode

import java.util.Base64

/**
 * Validates the metadata modern clients send with every request, both in the body and mirrored into headers
 * for intermediaries.
 */
internal object RequestMetadata {

    private val MethodHeader = ByteString("Mcp-Method")

    private val NameHeader = ByteString("Mcp-Name")

    private val Prefix = "=?base64?".toByteArray(Charsets.US_ASCII)

    private val Suffix = "?=".toByteArray(Charsets.US_ASCII)

    /**
     * A problem found with the metadata of a request.
     *
     * @property message Describes the problem to the client
     * @property code The JSON-RPC error code to respond with
     */
    class Problem(val message: String, val code: Int)

    /**
     * Validates the metadata of the given request.
     *
     * @return The problem found, or null if the metadata is valid
     */
    fun validate(request: Request, version: String, method: McpMethod, methodElement: JsonNode, parameters: JsonNode?): Problem? {
        val meta = tryGetMeta(parameters)
            ?: return Problem("The request does not carry the required _meta object", RpcErrors.InvalidParams)

        val metaVersion = meta.get(MetaKeys.ProtocolVersion)

        if (metaVersion == null || !metaVersion.isString) {
            return Problem("The _meta field '${MetaKeys.ProtocolVersion}' is required", RpcErrors.InvalidParams)
        }

        val capabilities = meta.get(MetaKeys.ClientCapabilities)

        if (capabilities == null || !capabilities.isObject) {
            return Problem("The _meta field '${MetaKeys.ClientCapabilities}' is required", RpcErrors.InvalidParams)
        }

        if (metaVersion.stringValue() != version) {
            return Problem("The MCP-Protocol-Version header does not match the protocol version given in _meta", RpcErrors.HeaderMismatch)
        }

        val headers = request.header.headers

        val methodHeader = headers.getEntry(MethodHeader)
            ?: return Problem("The Mcp-Method header is required", RpcErrors.HeaderMismatch)

        if (!methodElement.stringValue().toByteArray(Charsets.UTF_8).contentEquals(methodHeader.toByteArray())) {
            return Problem("The Mcp-Method header does not match the method of the request", RpcErrors.HeaderMismatch)
        }

        val name = parameters?.get("name")

        if (method == McpMethod.CallTool && name != null && name.isString) {
            val nameHeader = headers.getEntry(NameHeader)
                ?: return Problem("The Mcp-Name header is required", RpcErrors.HeaderMismatch)

            val decoded = decodeHeader(nameHeader.toByteArray())

            if (decoded == null || !name.stringValue().toByteArray(Charsets.UTF_8).contentEquals(decoded)) {
                return Problem("The Mcp-Name header does not match the name of the tool", RpcErrors.HeaderMismatch)
            }
        }

        return null
    }

    /**
     * Returns the `_meta` object of the given parameters, if present.
     */
    fun tryGetMeta(parameters: JsonNode?): JsonNode? {
        val meta = parameters?.takeIf { it.isObject }?.get("_meta")

        return if (meta != null && meta.isObject) meta else null
    }

    private fun decodeHeader(value: ByteArray): ByteArray? {
        if (value.size < Prefix.size + Suffix.size || !value.startsWith(Prefix) || !value.endsWith(Suffix)) {
            return value
        }

        val encoded = value.copyOfRange(Prefix.size, value.size - Suffix.size)

        return try {
            Base64.getDecoder().decode(encoded)
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun ByteString.toByteArray(): ByteArray {
        val view = bytes
        val result = ByteArray(view.remaining())

        view.get(result)

        return result
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean =
        prefix.indices.all { this[it] == prefix[it] }

    private fun ByteArray.endsWith(suffix: ByteArray): Boolean =
        suffix.indices.all { this[size - suffix.size + it] == suffix[it] }

}
