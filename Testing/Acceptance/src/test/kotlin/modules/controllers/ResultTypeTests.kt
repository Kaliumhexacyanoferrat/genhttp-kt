package org.genhttp.testing.acceptance.modules.controllers

import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseBuilder
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.controllers.Controller
import org.genhttp.modules.conversion.Serialization
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.from
import org.genhttp.modules.io.fromString
import org.genhttp.modules.io.strings.StringContent
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.reflection.ExecutionMode
import org.genhttp.modules.reflection.Injection
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineFrameworkTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import kotlin.test.assertEquals

class ResultTypeTests {

    // region Helpers

    private suspend fun getRunner(engine: ServerEngine, mode: ExecutionMode): TestHost {
        val controller = Controller.from<TestController>()
            .serializers(Serialization.default())
            .injectors(Injection.default())
            .executionMode(mode)

        return TestHost.run(Layout.create().add("t", controller), engine = engine)
    }

    // endregion

    // region Supporting data structures

    class TestController {

        fun handlerBuilder(): HandlerBuilder = Content.from(Resource.fromString("HandlerBuilder"))

        fun handler(parent: Handler): Handler = Content.from(Resource.fromString("Handler")).build()

        fun responseBuilder(request: Request): ResponseBuilder = request.respond().content(StringContent("ResponseBuilder"))

        fun response(request: Request): Response = request.respond().content(StringContent("Response")).build()

    }

    // endregion

    // region Tests

    @MultiEngineFrameworkTest
    fun controllerMayReturnHandlerBuilder(engine: ServerEngine, mode: ExecutionMode) = test {
        getRunner(engine, mode).use { runner ->
            val response = runner.getResponse("/t/handler-builder/")

            response.assertStatus(ResponseStatus.Ok)
            assertEquals("HandlerBuilder", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun controllerMayReturnHandler(engine: ServerEngine, mode: ExecutionMode) = test {
        getRunner(engine, mode).use { runner ->
            val response = runner.getResponse("/t/handler/")

            response.assertStatus(ResponseStatus.Ok)
            assertEquals("Handler", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun controllerMayReturnResponseBuilder(engine: ServerEngine, mode: ExecutionMode) = test {
        getRunner(engine, mode).use { runner ->
            val response = runner.getResponse("/t/response-builder/")

            response.assertStatus(ResponseStatus.Ok)
            assertEquals("ResponseBuilder", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun controllerMayReturnResponse(engine: ServerEngine, mode: ExecutionMode) = test {
        getRunner(engine, mode).use { runner ->
            val response = runner.getResponse("/t/response/")

            response.assertStatus(ResponseStatus.Ok)
            assertEquals("Response", response.getContent())
        }
    }

    // endregion

}
