package org.genhttp.modules.dependencyinjection.infrastructure

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.modules.dependencyinjection.RequestScope
import org.genhttp.modules.dependencyinjection.getServiceProvider
import org.genhttp.modules.dependencyinjection.getServiceScope
import org.genhttp.modules.reflection.injectors.ParameterInjector

import org.koin.core.annotation.KoinInternalApi

import kotlin.reflect.KClass

/**
 * Provides values for parameters of service methods from the container used by the server.
 */
public class DependencyInjector : ParameterInjector {

    @OptIn(KoinInternalApi::class)
    override fun supports(server: Server, type: KClass<*>): Boolean {
        val koin = server.getServiceProvider()

        val rootScope = koin.scopeRegistry.rootScope.scopeQualifier

        return koin.instanceRegistry.instances.values.any {
            val definition = it.beanDefinition

            definition.qualifier == null
                && (definition.scopeQualifier == rootScope || definition.scopeQualifier == RequestScope)
                && definition.hasType(type)
        }
    }

    override suspend fun getValue(handler: Handler, request: Request, targetType: KClass<*>): Any? =
        request.getServiceScope().getOrNull<Any>(targetType)

}
