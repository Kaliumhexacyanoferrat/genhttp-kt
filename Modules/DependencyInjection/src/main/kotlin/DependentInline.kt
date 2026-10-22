package org.genhttp.modules.dependencyinjection

import org.genhttp.api.infrastructure.Builder
import org.genhttp.modules.conversion.formatters.FormatterRegistry
import org.genhttp.modules.conversion.serializers.SerializationRegistry
import org.genhttp.modules.dependencyinjection.infrastructure.configure
import org.genhttp.modules.functional.Inline
import org.genhttp.modules.functional.provider.InlineBuilder
import org.genhttp.modules.reflection.injectors.InjectionRegistryBuilder

/**
 * Creates inline handlers with dependency injection enabled.
 */
public object DependentInline {

    /**
     * Creates a new line handler with dependency injection enabled on the declared methods.
     *
     * @param injectors Additional parameter injections to be used by the inline handler
     * @param serializers Additional serializers to be used by the inline handler
     * @param formatters Additional formatters to be used by the inline handler
     * @return The newly created inline handler
     */
    public fun create(
        injectors: InjectionRegistryBuilder? = null,
        serializers: Builder<SerializationRegistry>? = null,
        formatters: Builder<FormatterRegistry>? = null
    ): InlineBuilder = Inline.create().configure(injectors, serializers, formatters)

}
