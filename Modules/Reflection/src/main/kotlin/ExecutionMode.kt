package org.genhttp.modules.reflection

import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.Builder
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.modules.conversion.formatters.FormatterRegistry
import org.genhttp.modules.conversion.serializers.SerializationRegistry
import org.genhttp.modules.reflection.injectors.InjectionRegistry

/**
 * Configures how the methods of a service are executed.
 */
public enum class ExecutionMode {

    /**
     * Uses the fastest invocation strategy available (positional invocation where possible).
     */
    Auto,

    /**
     * Always invokes methods via `callSuspendBy` (named arguments).
     */
    Reflection

}
