package org.genhttp.modules.dependencyinjection.basics

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.modules.dependencyinjection.DependentHandler
import org.genhttp.modules.dependencyinjection.infrastructure.InstanceProvider

import kotlin.reflect.KClass

internal class HandlerIntegration<T : DependentHandler>(private val type: KClass<T>) : Handler {

    override suspend fun prepare(server: Server) {
        // nothing to prepare, the lifecycle is managed by the container
    }

    override suspend fun handle(request: Request): Response? {
        val instance = InstanceProvider.provide(request, type)

        return instance.handle(request)
    }

}
