package org.genhttp.modules.reflection

import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.Builder
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.modules.conversion.formatters.FormatterRegistry
import org.genhttp.modules.conversion.serializers.SerializationRegistry
import org.genhttp.modules.reflection.injectors.InjectionRegistry

/**
 * Allows to configure the registries of a service framework.
 */
public interface RegistryBuilder<out T> {

    /**
     * Configures the serialization formats used to read and write bodies.
     */
    public fun serializers(registry: Builder<SerializationRegistry>): T

    /**
     * Configures the injectors used to provide method arguments.
     */
    public fun injectors(registry: Builder<InjectionRegistry>): T

    /**
     * Configures the formatters used to convert simple values.
     */
    public fun formatters(registry: Builder<FormatterRegistry>): T

}
