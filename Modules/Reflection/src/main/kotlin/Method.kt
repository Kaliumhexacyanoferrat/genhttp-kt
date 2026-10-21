package org.genhttp.modules.reflection

import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.Builder
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.modules.conversion.formatters.FormatterRegistry
import org.genhttp.modules.conversion.serializers.SerializationRegistry
import org.genhttp.modules.reflection.injectors.InjectionRegistry

/**
 * HTTP request methods that can be used in annotations (annotation parameters cannot reference [RequestMethod]).
 */
public enum class Method {
    Get,
    Head,
    Post,
    Put,
    Delete,
    Connect,
    Options,
    Trace,
    Patch
}

/**
 * Converts the given method into the corresponding request method.
 */
public fun Method.toRequestMethod(): RequestMethod = RequestMethod(name.uppercase())
