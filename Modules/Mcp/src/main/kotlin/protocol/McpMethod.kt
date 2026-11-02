package org.genhttp.modules.mcp.protocol

import tools.jackson.databind.JsonNode

/**
 * The methods of the protocol known to the handler.
 */
internal enum class McpMethod {
    Unknown,
    Initialize,
    Discover,
    Ping,
    ListTools,
    CallTool,
    ListResources,
    ListResourceTemplates,
    ListPrompts
}

internal object McpMethods {

    private val Known: List<Pair<String, McpMethod>> = listOf(
        "tools/call" to McpMethod.CallTool,
        "tools/list" to McpMethod.ListTools,
        "server/discover" to McpMethod.Discover,
        "initialize" to McpMethod.Initialize,
        "ping" to McpMethod.Ping,
        "resources/list" to McpMethod.ListResources,
        "resources/templates/list" to McpMethod.ListResourceTemplates,
        "prompts/list" to McpMethod.ListPrompts
    )

    /**
     * Identifies the given method (a string element).
     *
     * @return The method and its name, if known (the name is null for unknown methods)
     */
    fun identify(method: JsonNode): Pair<McpMethod, String?> {
        val value = method.stringValue()

        for ((name, known) in Known) {
            if (name == value) {
                return known to name
            }
        }

        return McpMethod.Unknown to null
    }

}
