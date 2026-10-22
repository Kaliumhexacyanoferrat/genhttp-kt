package org.genhttp.modules.dependencyinjection.infrastructure

import org.genhttp.api.protocol.Request
import org.genhttp.modules.dependencyinjection.getServiceScope

import kotlin.reflect.KClass

@PublishedApi
internal object HandlerResolver {

    @PublishedApi
    internal fun <T : Any> obtain(request: Request, type: KClass<T>): T {
        val scope = request.getServiceScope()

        val instance = scope.getOrNull<Any>(type)
            ?: Activator.create(type, scope)

        return type.javaObjectType.cast(instance)
            ?: throw IllegalStateException("Unable to retrieve handler of type '${type.qualifiedName ?: type}' from service scope.")
    }

}
