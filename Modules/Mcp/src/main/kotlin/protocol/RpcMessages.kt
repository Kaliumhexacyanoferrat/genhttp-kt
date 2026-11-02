package org.genhttp.modules.mcp.protocol

import tools.jackson.core.JsonGenerator
import tools.jackson.databind.JsonNode

import kotlin.time.Duration

/**
 * Writes the JSON-RPC messages sent by the server, adding the fields required by the protocol version of the
 * client.
 */
internal class RpcMessages(name: String, version: String, title: String?, cacheDuration: Duration) {

    private val cacheDuration: Long = cacheDuration.inWholeMilliseconds

    /**
     * The serialized information about this server.
     */
    val serverInfo: String

    init {
        JsonBuffer.rent().use { buffer ->
            val w = buffer.writer

            w.writeStartObject()
            w.writeStringProperty("name", name)

            if (title != null) {
                w.writeStringProperty("title", title)
            }

            w.writeStringProperty("version", version)
            w.writeEndObject()

            serverInfo = buffer.flush()
        }
    }

    // region Functionality

    /**
     * Completes a result message started via [beginResult].
     */
    fun endResult(buffer: JsonBuffer, modern: Boolean, cacheable: Boolean): ByteArray {
        buffer.use {
            val w = buffer.writer

            if (modern) {
                w.writeStringProperty("resultType", "complete")

                if (cacheable) {
                    w.writeNumberProperty("ttlMs", cacheDuration)
                    w.writeStringProperty("cacheScope", "public")
                }

                w.writeObjectPropertyStart("_meta")
                w.writeName(MetaKeys.ServerInfo)
                w.writeRawValue(serverInfo)
                w.writeEndObject()
            }

            w.writeEndObject()
            w.writeEndObject()

            return buffer.toArray()
        }
    }

    // endregion

    companion object {

        /**
         * Starts a result message, to be completed via [endResult].
         */
        fun beginResult(id: JsonNode): JsonBuffer {
            val buffer = JsonBuffer.rent()
            val w = buffer.writer

            w.writeStartObject()
            w.writeStringProperty("jsonrpc", "2.0")

            w.writeName("id")
            w.writeTree(id)

            w.writeObjectPropertyStart("result")

            return buffer
        }

        fun error(id: JsonNode?, code: Int, message: String): ByteArray {
            beginError(id, code, message).use { buffer ->
                buffer.writer.writeEndObject()
                buffer.writer.writeEndObject()

                return buffer.toArray()
            }
        }

        fun unsupportedVersion(id: JsonNode, requested: String): ByteArray {
            beginError(id, RpcErrors.UnsupportedProtocolVersion, "Unsupported protocol version").use { buffer ->
                val w = buffer.writer

                w.writeObjectPropertyStart("data")
                writeSupportedVersions(w, "supported")
                w.writeStringProperty("requested", requested)
                w.writeEndObject()

                w.writeEndObject()
                w.writeEndObject()

                return buffer.toArray()
            }
        }

        fun writeSupportedVersions(writer: JsonGenerator, property: String) {
            writer.writeArrayPropertyStart(property)

            for (supported in McpVersions.Supported) {
                writer.writeString(supported)
            }

            writer.writeEndArray()
        }

        private fun beginError(id: JsonNode?, code: Int, message: String): JsonBuffer {
            val buffer = JsonBuffer.rent()
            val w = buffer.writer

            w.writeStartObject()
            w.writeStringProperty("jsonrpc", "2.0")

            if (id != null) {
                w.writeName("id")
                w.writeTree(id)
            }

            w.writeObjectPropertyStart("error")
            w.writeNumberProperty("code", code)
            w.writeStringProperty("message", message)

            return buffer
        }

    }

}
