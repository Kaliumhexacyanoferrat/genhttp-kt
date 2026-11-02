package org.genhttp.modules.mcp

import org.genhttp.modules.mcp.provider.ToolHints

/**
 * Marks a function of a class registered via [org.genhttp.modules.mcp.provider.ToolsHandlerBuilder.add]
 * as a tool that can be invoked by an AI agent.
 *
 * On a function reference added via `add(name, description, ::function)`, the annotation provides the title
 * and the hints of the tool:
 *
 * ```kotlin
 * @Tool(readOnly = [true])
 * fun getTime() = Instant.now()
 *
 * Tools.create().add("get_time", "Returns the current time", ::getTime)
 * ```
 *
 * The tool is described by the [org.genhttp.api.content.documentation.Summary] or the
 * [com.fasterxml.jackson.annotation.JsonPropertyDescription] annotation on the function. Its parameters become
 * the arguments of the tool, except for the ones that are injected by the framework (such as the request
 * or the authenticated user). Interceptors (such as role checks) are executed before the function is invoked.
 *
 * The hints are tri-state (hints that are not set are not sent to the client), so they are declared as arrays
 * with at most one entry (`@Tool(readOnly = [true])`).
 *
 * @property name The name of the tool (defaults to the name of the function in snake case, e.g. "create_user"
 * for "createUserAsync"; empty, if not set)
 * @property title A human-readable name of the tool, shown by clients (empty, if not set)
 * @property readOnly The tool does not modify its environment (empty, if not set)
 * @property destructive The tool may perform destructive updates (empty, if not set)
 * @property idempotent Calling the tool repeatedly with the same arguments has no additional effect (empty, if not set)
 * @property openWorld The tool interacts with external entities, such as the web (empty, if not set)
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
public annotation class Tool(
    val name: String = "",
    val title: String = "",
    val readOnly: BooleanArray = [],
    val destructive: BooleanArray = [],
    val idempotent: BooleanArray = [],
    val openWorld: BooleanArray = []
)

/**
 * The name of the tool, if specified.
 */
internal val Tool.toolName: String?
    get() = name.ifEmpty { null }

/**
 * The title of the tool, if specified.
 */
internal val Tool.toolTitle: String?
    get() = title.ifEmpty { null }

/**
 * The hints declared by the annotation, or null if none of them has been set.
 */
internal val Tool.hints: ToolHints?
    get() = if (readOnly.isEmpty() && destructive.isEmpty() && idempotent.isEmpty() && openWorld.isEmpty()) {
        null
    } else {
        ToolHints(readOnly.firstOrNull(), destructive.firstOrNull(), idempotent.firstOrNull(), openWorld.firstOrNull())
    }
