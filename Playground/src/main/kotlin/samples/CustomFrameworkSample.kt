package org.genhttp.playground.samples

import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.Response
import org.genhttp.modules.apibrowsing.addScalar
import org.genhttp.modules.conversion.Formatting
import org.genhttp.modules.conversion.Serialization
import org.genhttp.modules.functional.provider.InlineMethodConfiguration
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.openapi.addOpenApi
import org.genhttp.modules.reflection.ExecutionMode
import org.genhttp.modules.reflection.ExecutionSettings
import org.genhttp.modules.reflection.Injection
import org.genhttp.modules.reflection.MethodCollection
import org.genhttp.modules.reflection.MethodHandler
import org.genhttp.modules.reflection.MethodRegistry
import org.genhttp.modules.reflection.ServiceMethodProvider
import org.genhttp.modules.reflection.operations.OperationBuilder

object CustomFrameworkSample {

    fun create(): HandlerBuilder {
        /*
         *
         * Shows how to implement a custom framework.
         *
         * See https://genhttp.org/documentation/content/frameworks/custom/
         *
         */
        return Layout.create()
            .add(CustomFrameworkHandler())
            .addOpenApi()
            .addScalar()
    }

    class CustomFrameworkHandler : Handler, ServiceMethodProvider {

        private var _methods: MethodCollection? = null

        override val methods: MethodCollection
            get() = _methods ?: throw IllegalStateException("Handler is not prepared yet")

        override suspend fun prepare(server: Server) {
            val list = mutableListOf<MethodHandler>()

            // specify the supported methods of the operation we are exposing
            val supportedMethods = InlineMethodConfiguration(setOf(RequestMethod.Get))

            // the actual piece of code to be executed - either a function or a lambda
            val function = this::exposedMethod

            // auto enables the positional fast path, otherwise named reflection only
            val executionSettings = ExecutionSettings(ExecutionMode.Auto)

            // configures the behavior for serialization, injection and formatting
            val registry = MethodRegistry(
                Serialization.default().build(),
                Injection.default().build(),
                Formatting.default().build()
            )

            // build the operation we would like to provide
            val operation = OperationBuilder.create(server, ":id", function, null, executionSettings, supportedMethods, registry)

            // create a method handler from the operation which will serve it as an HTTP endpoint
            // this handler requires an instance provider which tells the framework on which
            // object to invoke the given function (not needed for bound references like this one)
            list.add(MethodHandler(operation, { this }, registry))

            // build a method collection handler from all collected operations
            // this handler is responsible for routing
            val methods = MethodCollection(list)
            methods.prepare(server)

            _methods = methods
        }

        override suspend fun handle(request: Request): Response? = methods.handle(request)

        fun exposedMethod(id: Int): String = id.toString()

    }

}
