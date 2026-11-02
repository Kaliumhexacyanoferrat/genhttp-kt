package org.genhttp.modules.mcp

import org.genhttp.modules.mcp.provider.ToolsHandlerBuilder

/**
 * Provides tools to AI agents such as Claude via the Model Context Protocol (MCP).
 *
 * ```kotlin
 * val mcp = Tools.create()
 *     .server("weather", "1.0.0")
 *     .add<WeatherTools>()
 *     .add("add", "Adds two numbers") { a: Int, b: Int -> a + b }
 *
 * val app = Layout.create()
 *     .add("mcp", mcp)
 * ```
 */
public object Tools {

    /**
     * Creates a new endpoint to add tools to.
     */
    public fun create(): ToolsHandlerBuilder = ToolsHandlerBuilder()

}
