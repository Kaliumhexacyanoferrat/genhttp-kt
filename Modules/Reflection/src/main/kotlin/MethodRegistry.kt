package org.genhttp.modules.reflection

import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.Builder
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.modules.conversion.formatters.FormatterRegistry
import org.genhttp.modules.conversion.serializers.SerializationRegistry
import org.genhttp.modules.reflection.injectors.InjectionRegistry

/**
 * The registries used to execute the methods of a service.
 */
public data class MethodRegistry(
    val serialization: SerializationRegistry,
    val injection: InjectionRegistry,
    val formatting: FormatterRegistry
)
