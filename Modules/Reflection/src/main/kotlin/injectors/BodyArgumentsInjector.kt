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
 * Injects the form encoded arguments sent with the request body.
 */
public class BodyArgumentsInjector : ParameterInjector {

    override fun supports(server: Server, type: KClass<*>): Boolean = type == BodyArguments::class

    override suspend fun getValue(handler: Handler, request: Request, targetType: KClass<*>): Any {
        val body = request.getBody()
        return body?.asBodyArguments() ?: BodyArguments.Empty
    }

}
