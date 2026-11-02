package org.genhttp.modules.webservices.provider

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.modules.reflection.ExecutionSettings
import org.genhttp.modules.reflection.MethodCollection
import org.genhttp.modules.reflection.MethodHandler
import org.genhttp.modules.reflection.MethodRegistry
import org.genhttp.modules.reflection.ServiceMethodProvider
import org.genhttp.modules.reflection.operations.OperationBuilder
import org.genhttp.modules.reflection.operations.TypeAnalyzer
import org.genhttp.modules.webservices.ResourceMethod
import org.genhttp.modules.webservices.ResourceMethodConfiguration

import kotlin.reflect.KClass
import kotlin.reflect.full.findAnnotation

/**
 * Routes requests to the operations of a webservice.
 */
public class ServiceResourceRouter(
    private val type: KClass<*>,
    private val instanceProvider: suspend (Request) -> Any,
    private val executionSettings: ExecutionSettings,
    private val registry: MethodRegistry
) : Handler, ServiceMethodProvider {

    private var _methods: MethodCollection? = null

    override val methods: MethodCollection
        get() = _methods ?: throw IllegalStateException("Handler is not prepared yet")

    override suspend fun prepare(server: Server) {
        val found = mutableListOf<MethodHandler>()

        for (method in TypeAnalyzer.findMethods(type)) {
            val annotation = method.findAnnotation<ResourceMethod>() ?: continue

            val configuration = ResourceMethodConfiguration(annotation)

            val operation = OperationBuilder.create(server, configuration.path, method, null, executionSettings, configuration, registry)

            found.add(MethodHandler(operation, instanceProvider, registry))
        }

        val result = MethodCollection(found)

        result.prepare(server)

        _methods = result
    }

    override suspend fun handle(request: Request): Response? = methods.handle(request)

}
