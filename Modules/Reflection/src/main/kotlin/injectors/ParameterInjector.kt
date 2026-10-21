package org.genhttp.modules.reflection.injectors

import org.genhttp.api.content.Handler
import org.genhttp.api.content.ProviderException
import org.genhttp.api.content.authentication.User
import org.genhttp.api.infrastructure.Builder
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.io.BodyArguments
import org.genhttp.modules.io.asBodyArguments

import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf

/**
 * Provides values for parameters of service methods based on their type (e.g. the current request).
 */
public interface ParameterInjector {

    /**
     * Checks whether this injector can provide values of the given type.
     */
    public fun supports(server: Server, type: KClass<*>): Boolean

    /**
     * Provides the value for a parameter of the given type.
     *
     * @param handler The handler executing the service method
     * @param request The request currently handled
     * @param targetType The type of the parameter
     */
    public suspend fun getValue(handler: Handler, request: Request, targetType: KClass<*>): Any?

}
