package org.genhttp.modules.mcp.provider

import org.genhttp.api.content.Handler
import org.genhttp.api.protocol.Request

/**
 * The request a tool is invoked for and the handler serving it.
 */
internal class ToolContext(val request: Request, val handler: Handler)
