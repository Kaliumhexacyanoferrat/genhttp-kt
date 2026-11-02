package org.genhttp.modules.mcp.provider

import org.slf4j.Logger

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * The log messages of the handler (`LoggerMessage` definitions upstream).
 */
internal object McpLog {

    /**
     * The category the handler logs with.
     */
    const val Category: String = "org.genhttp.modules.mcp"

    fun Logger.operationHandled(method: String, elapsed: Double, version: String) {
        info("MCP {} answered in {} ms (protocol {})", method, format(elapsed), version)
    }

    fun Logger.clientHandled(method: String, client: String, clientVersion: String, elapsed: Double, version: String) {
        info("MCP {} from {} {} answered in {} ms (protocol {})", method, client, clientVersion, format(elapsed), version)
    }

    fun Logger.toolSucceeded(tool: String, elapsed: Double) {
        info("MCP tool {} succeeded in {} ms", tool, format(elapsed))
    }

    fun Logger.toolFailed(tool: String, elapsed: Double, reason: String) {
        info("MCP tool {} returned an error in {} ms: {}", tool, format(elapsed), reason)
    }

    fun Logger.toolCrashed(exception: Throwable, tool: String, elapsed: Double) {
        error("MCP tool {} failed unexpectedly after {} ms", tool, format(elapsed), exception)
    }

    fun Logger.requestRejected(status: Int, elapsed: Double, reason: String) {
        info("MCP request rejected with HTTP {} in {} ms: {}", status, format(elapsed), reason)
    }

    fun Logger.notificationAccepted(method: String) {
        debug("MCP notification {} accepted", method)
    }

    /**
     * Formats an elapsed time like `{Elapsed:0.##}`.
     */
    private fun format(elapsed: Double): String = DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT)).format(elapsed)

}
