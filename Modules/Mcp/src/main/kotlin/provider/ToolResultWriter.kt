package org.genhttp.modules.mcp.provider

import tools.jackson.core.JsonGenerator

/**
 * Writes the outcome of a tool call as the body of a call result.
 *
 * JSON data is sent as structured content and, for clients that only read the content blocks, also as text.
 * Legacy protocol versions only accept objects as structured content, scalar values are only sent as text.
 */
internal object ToolResultWriter {

    fun write(writer: JsonGenerator, outcome: ToolOutcome, modern: Boolean) {
        val content = outcome.result.content
        val structured = outcome.structured

        writer.writeArrayPropertyStart("content")

        if (content.isEmpty() && structured != null) {
            writer.writeStartObject()
            writer.writeStringProperty("type", "text")
            writer.writeStringProperty("text", structured)
            writer.writeEndObject()
        }

        for (block in content) {
            block.write(writer)
        }

        writer.writeEndArray()

        if (structured != null && structured.isNotEmpty() && (structured[0] == '{' || (modern && structured[0] == '['))) {
            writer.writeName("structuredContent")
            writer.writeRawValue(structured)
        }

        writer.writeBooleanProperty("isError", outcome.result.isError)
    }

}
