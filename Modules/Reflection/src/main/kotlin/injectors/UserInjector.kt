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
 * Injects the authenticated user of the given type (as set by the authentication concerns).
 *
 * @param type The type of user to be injected
 */
public class UserInjector<T : User>(private val type: KClass<T>) : ParameterInjector {

    override fun supports(server: Server, type: KClass<*>): Boolean = type == this.type

    override suspend fun getValue(handler: Handler, request: Request, targetType: KClass<*>): Any {
        val user = request.properties.tryGet("__AUTH_USER", User::class)

        if (user != null && type.isSuperclassOf(user::class)) {
            return user
        }

        throw ProviderException(ResponseStatus.Unauthorized, "Authentication required to invoke this endpoint")
    }

}
