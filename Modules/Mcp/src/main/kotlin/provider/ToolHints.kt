package org.genhttp.modules.mcp.provider

/**
 * Hints describing the effects of a tool, as declared via [org.genhttp.modules.mcp.Tool].
 *
 * Hints that are not set are not sent, so clients assume the defaults of the specification.
 */
internal data class ToolHints(val readOnly: Boolean?, val destructive: Boolean?, val idempotent: Boolean?, val openWorld: Boolean?)
