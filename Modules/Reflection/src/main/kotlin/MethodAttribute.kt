package org.genhttp.modules.reflection

import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.Builder
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.modules.conversion.formatters.FormatterRegistry
import org.genhttp.modules.conversion.serializers.SerializationRegistry
import org.genhttp.modules.reflection.injectors.InjectionRegistry

/**
 * Specifies the request methods supported by a service method.
 *
 * In GenHTTP this is the base class of the method attributes. As Kotlin annotations cannot inherit, this class
 * is used programmatically (e.g. by frameworks converting their annotations into a configuration).
 */
public open class MethodAttribute : MethodConfiguration {

    final override val supportedMethods: Set<RequestMethod>

    /**
     * Supports GET and HEAD requests.
     */
    public constructor() {
        supportedMethods = setOf(RequestMethod.Get, RequestMethod.Head)
    }

    /**
     * Supports the given request methods.
     */
    public constructor(vararg methods: Method) {
        supportedMethods = methods.map { it.toRequestMethod() }.toSet()
    }

}
