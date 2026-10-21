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
 * Injects the handler executing the service method.
 */
public class HandlerInjector : ParameterInjector {

    override fun supports(server: Server, type: KClass<*>): Boolean = type == Handler::class

    override suspend fun getValue(handler: Handler, request: Request, targetType: KClass<*>): Any = handler

}
