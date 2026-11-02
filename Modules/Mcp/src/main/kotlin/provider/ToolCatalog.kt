package org.genhttp.modules.mcp.provider

import org.genhttp.api.infrastructure.Server
import org.genhttp.modules.mcp.protocol.JsonBuffer
import org.genhttp.modules.reflection.MethodRegistry

import tools.jackson.databind.json.JsonMapper

/**
 * The tools offered by a handler, with their definitions encoded once so the tool list can be sent without
 * serializing it again.
 *
 * @property list The serialized definitions of the tools (a JSON array)
 */
internal class ToolCatalog private constructor(private val tools: List<RegisteredTool>, val list: String) {

    // region Functionality

    fun find(name: String): RegisteredTool? = tools.firstOrNull { it.name == name }

    // endregion

    companion object {

        fun create(server: Server, definitions: List<MethodToolDefinition>, registry: MethodRegistry, serialization: JsonMapper): ToolCatalog {
            val tools = definitions.map { MethodToolFactory.create(server, it, registry, serialization) }

            JsonBuffer.rent().use { buffer ->
                buffer.writer.writeStartArray()

                for (tool in tools) {
                    tool.writeDefinition(buffer.writer)
                }

                buffer.writer.writeEndArray()

                return ToolCatalog(tools, buffer.flush())
            }
        }

    }

}
