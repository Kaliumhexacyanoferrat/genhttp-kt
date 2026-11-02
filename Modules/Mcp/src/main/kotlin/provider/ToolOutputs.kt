package org.genhttp.modules.mcp.provider

import org.genhttp.api.content.ProviderException
import org.genhttp.modules.mcp.ToolResult
import org.genhttp.modules.mcp.protocol.JsonBuffer

import tools.jackson.databind.json.JsonMapper

import kotlin.coroutines.cancellation.CancellationException

/**
 * Converts whatever a tool returned or threw into an outcome.
 */
internal object ToolOutputs {

    private val NoContent = ToolResult(emptyList())

    fun capture(output: Any?, mapper: JsonMapper): ToolOutcome {
        if (output is String) {
            return ToolOutcome(ToolResult.text(output))
        }

        if (output is ToolResult) {
            val structured = output.structured

            return if (structured != null) serialize(output, structured, mapper) else ToolOutcome(output)
        }

        return serialize(NoContent, output, mapper)
    }

    fun empty(): ToolOutcome = ToolOutcome(NoContent)

    fun fail(exception: Throwable): ToolOutcome =
        if (exception is ProviderException) ToolOutcome.failed(exception.message ?: exception.status.toString()) else ToolOutcome.crashed(exception)

    private fun serialize(result: ToolResult, data: Any?, mapper: JsonMapper): ToolOutcome {
        val json = try {
            JsonBuffer.rent().use { buffer ->
                // the generator of the buffer does not escape beyond JSON, independent of the given mapper
                mapper.writeValue(buffer.writer, data)
                buffer.flush()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ToolOutcome.crashed(e)
        }

        return ToolOutcome(result, json)
    }

}
