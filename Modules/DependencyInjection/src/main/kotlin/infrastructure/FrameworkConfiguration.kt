package org.genhttp.modules.dependencyinjection.infrastructure

import org.genhttp.api.infrastructure.Builder
import org.genhttp.modules.conversion.formatters.FormatterRegistry
import org.genhttp.modules.conversion.serializers.SerializationRegistry
import org.genhttp.modules.reflection.Injection
import org.genhttp.modules.reflection.RegistryBuilder
import org.genhttp.modules.reflection.injectors.InjectionRegistryBuilder

internal fun <T : RegistryBuilder<T>> T.configure(
    injectors: InjectionRegistryBuilder? = null,
    serializers: Builder<SerializationRegistry>? = null,
    formatters: Builder<FormatterRegistry>? = null
): T {
    val injectionOverlay = injectors ?: Injection.default()

    injectionOverlay.add(DependencyInjector())

    this.injectors(injectionOverlay)

    if (serializers != null) {
        this.serializers(serializers)
    }

    if (formatters != null) {
        this.formatters(formatters)
    }

    return this
}
