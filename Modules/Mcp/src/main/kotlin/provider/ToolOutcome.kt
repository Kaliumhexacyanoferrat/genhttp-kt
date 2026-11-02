package org.genhttp.modules.mcp.provider

import org.genhttp.modules.mcp.ToolResult

/**
 * What a tool produced, before it is written as a protocol message.
 *
 * @property result The content to be presented to the model
 * @property structured The result serialized as JSON, if any
 * @property crash The unexpected exception the call failed with, if any
 */
internal class ToolOutcome(val result: ToolResult, val structured: String? = null, val crash: Throwable? = null) {

    companion object {

        fun failed(message: String): ToolOutcome = ToolOutcome(ToolResult.error(message))

        fun crashed(exception: Throwable): ToolOutcome =
            ToolOutcome(ToolResult.error("The tool failed unexpectedly: ${exception.message ?: exception.toString()}"), crash = exception)

    }

}
