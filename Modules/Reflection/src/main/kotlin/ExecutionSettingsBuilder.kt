package org.genhttp.modules.reflection

import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.Builder
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.modules.conversion.formatters.FormatterRegistry
import org.genhttp.modules.conversion.serializers.SerializationRegistry
import org.genhttp.modules.reflection.injectors.InjectionRegistry

/**
 * Allows to configure the execution of a service framework.
 */
public interface ExecutionSettingsBuilder<out T> {

    /**
     * Sets the mode used to invoke the methods of the service.
     */
    public fun executionMode(mode: ExecutionMode): T

}
