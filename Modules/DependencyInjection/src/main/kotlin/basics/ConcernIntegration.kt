package org.genhttp.modules.dependencyinjection.basics

import org.genhttp.api.content.Concern
import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.modules.dependencyinjection.DependentConcern
import org.genhttp.modules.dependencyinjection.infrastructure.InstanceProvider

import kotlin.reflect.KClass

internal class ConcernIntegration<T : DependentConcern>(
    override val content: Handler,
    private val type: KClass<T>
) : Concern {

    override suspend fun prepare(server: Server): Unit = content.prepare(server)

    override suspend fun handle(request: Request): Response? {
        val instance = InstanceProvider.provide(request, type)

        return instance.handle(content, request)
    }

}
