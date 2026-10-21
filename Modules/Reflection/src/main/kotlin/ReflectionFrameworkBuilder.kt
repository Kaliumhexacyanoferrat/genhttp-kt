package org.genhttp.modules.reflection

import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.Builder
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.modules.conversion.formatters.FormatterRegistry
import org.genhttp.modules.conversion.serializers.SerializationRegistry
import org.genhttp.modules.reflection.injectors.InjectionRegistry

/**
 * Common interface of the builders of the service frameworks (inline handlers, webservices, controllers).
 */
public interface ReflectionFrameworkBuilder<out T : HandlerBuilder.WithConcerns<T>> :
    HandlerBuilder.WithConcerns<T>, RegistryBuilder<T>, ExecutionSettingsBuilder<T>
