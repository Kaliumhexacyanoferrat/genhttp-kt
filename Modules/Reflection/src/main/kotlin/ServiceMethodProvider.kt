package org.genhttp.modules.reflection

import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.Builder
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.modules.conversion.formatters.FormatterRegistry
import org.genhttp.modules.conversion.serializers.SerializationRegistry
import org.genhttp.modules.reflection.injectors.InjectionRegistry

/**
 * Provides the methods of a service.
 */
public interface ServiceMethodProvider {

    /**
     * The methods provided by the service.
     */
    public val methods: MethodCollection

}
