package org.genhttp.modules.mcp.provider

import tools.jackson.core.JsonGenerator
import tools.jackson.databind.node.ObjectNode

/**
 * A tool as offered by the handler, with everything needed to describe and to invoke it.
 */
internal class RegisteredTool(
    val name: String,
    private val title: String?,
    private val description: String,
    private val hints: ToolHints?,
    private val inputSchema: ObjectNode,
    private val outputSchema: ObjectNode?,
    val invoker: MethodToolInvoker
) {

    init {
        ToolNames.validate(name)
    }

    // region Functionality

    fun writeDefinition(writer: JsonGenerator) {
        writer.writeStartObject()

        writer.writeStringProperty("name", name)

        if (title != null) {
            writer.writeStringProperty("title", title)
        }

        writer.writeStringProperty("description", description)

        writer.writeName("inputSchema")
        writer.writeTree(inputSchema)

        if (outputSchema != null) {
            writer.writeName("outputSchema")
            writer.writeTree(outputSchema)
        }

        if (hints != null || title != null) {
            writer.writeObjectPropertyStart("annotations")

            if (title != null) {
                writer.writeStringProperty("title", title)
            }

            writeHint(writer, "readOnlyHint", hints?.readOnly)
            writeHint(writer, "destructiveHint", hints?.destructive)
            writeHint(writer, "idempotentHint", hints?.idempotent)
            writeHint(writer, "openWorldHint", hints?.openWorld)

            writer.writeEndObject()
        }

        writer.writeEndObject()
    }

    private fun writeHint(writer: JsonGenerator, name: String, value: Boolean?) {
        if (value != null) {
            writer.writeBooleanProperty(name, value)
        }
    }

    // endregion

}

internal object ToolNames {

    fun validate(name: String?) {
        if (name.isNullOrEmpty() || name.length > 128) {
            throw IllegalStateException("The name of a tool needs to be between 1 and 128 characters long, but '$name' is not.")
        }

        for (c in name) {
            if (!(c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9') && c != '_' && c != '-' && c != '.') {
                throw IllegalStateException("The name of tool '$name' may only contain ASCII letters, digits, '_', '-' and '.'.")
            }
        }
    }

    /**
     * Derives a tool name from a function name, e.g. "create_user" from "createUserAsync".
     */
    fun fromMethod(method: String): String {
        var source = method

        if (source.endsWith("Async") && source.length > 5) {
            source = source.substring(0, source.length - 5)
        }

        val name = StringBuilder(source.length + 8)

        for (i in source.indices) {
            val c = source[i]

            if (c.isUpperCase() && i > 0) {
                val previous = source[i - 1]
                val next = if (i + 1 < source.length) source[i + 1] else '\u0000'

                if (previous.isLowerCase() || previous.isDigit() || (previous.isUpperCase() && next.isLowerCase())) {
                    name.append('_')
                }
            }

            name.append(c.lowercaseChar())
        }

        return name.toString()
    }

}
