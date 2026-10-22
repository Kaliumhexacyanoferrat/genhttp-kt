package org.genhttp.modules.dependencyinjection.infrastructure

import org.genhttp.api.protocol.Request
import org.genhttp.modules.dependencyinjection.getServiceScope

import kotlin.reflect.KClass

/**
 * Resolves instances from the scope of the current request, or constructs them
 * (with their dependencies resolved from the scope) if they are not registered.
 */
public object InstanceProvider {

    /**
     * Provides an instance of the given type.
     *
     * @param request The request to obtain the service scope from
     * @param type The type of the instance to be provided
     * @return The instance registered with the container or a newly constructed one
     * @throws IllegalStateException Thrown if dependency injection is not enabled on the host or the instance cannot be constructed
     */
    public suspend fun <T : Any> provide(request: Request, type: KClass<T>): T {
        val scope = request.getServiceScope()

        val instance = scope.getOrNull<Any>(type)
            ?: Activator.create(type, scope)

        return type.javaObjectType.cast(instance)
            ?: throw IllegalStateException("Unable to resolve or construct instance of type '${type.qualifiedName ?: type}'")
    }

    /**
     * Provides an instance of the given type.
     *
     * @param T The type of the instance to be provided
     * @param request The request to obtain the service scope from
     * @return The instance registered with the container or a newly constructed one
     * @throws IllegalStateException Thrown if dependency injection is not enabled on the host or the instance cannot be constructed
     */
    public suspend inline fun <reified T : Any> provide(request: Request): T = provide(request, T::class)

}
