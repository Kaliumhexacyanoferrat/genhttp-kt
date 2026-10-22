package org.genhttp.testing.acceptance.modules.controllers

import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.controllers.ControllerAction
import org.genhttp.modules.controllers.FromPath
import org.genhttp.modules.controllers.addController
import org.genhttp.modules.controllers.indexController
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.from
import org.genhttp.modules.io.fromString
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.reflection.ExecutionMode
import org.genhttp.modules.reflection.Method
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineFrameworkTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import java.net.http.HttpRequest.BodyPublishers

import kotlin.test.assertEquals

class ActionTests {

    // region Helpers

    private suspend fun getRunner(engine: ServerEngine, mode: ExecutionMode): TestHost =
        TestHost.run(Layout.create().addController<TestController>("t", mode = mode), engine = engine)

    // endregion

    // region Supporting data structures

    class Model {

        var field: String? = null

    }

    class TestController {

        fun index(): String = "Hello World!"

        fun action(query: Int?): HandlerBuilder = Content.from(Resource.fromString(query?.toString() ?: "Action"))

        @ControllerAction(Method.Put)
        fun action(value1: Int?, value2: String): HandlerBuilder = Content.from(Resource.fromString((value1?.toString() ?: "Action") + " $value2"))

        fun simpleAction(@FromPath id: Int): HandlerBuilder = Content.from(Resource.fromString(id.toString()))

        fun complexAction(three: Int, @FromPath one: Int, @FromPath two: Int): HandlerBuilder = Content.from(Resource.fromString((one + two + three).toString()))

        @ControllerAction(Method.Post)
        fun action(data: Model): HandlerBuilder = Content.from(Resource.fromString(data.field ?: "no content"))

        fun hypenCAsing99(): HandlerBuilder = Content.from(Resource.fromString("OK"))

        fun void() {
        }

    }

    // endregion

    // region Tests

    @MultiEngineFrameworkTest
    fun testIndex(engine: ServerEngine, mode: ExecutionMode) = test {
        getRunner(engine, mode).use { runner ->
            val response = runner.getResponse("/t/")

            response.assertStatus(ResponseStatus.Ok)
            assertEquals("Hello World!", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testAction(engine: ServerEngine, mode: ExecutionMode) = test {
        getRunner(engine, mode).use { runner ->
            val response = runner.getResponse("/t/action/")

            response.assertStatus(ResponseStatus.Ok)
            assertEquals("Action", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testActionWithQuery(engine: ServerEngine, mode: ExecutionMode) = test {
        getRunner(engine, mode).use { runner ->
            val response = runner.getResponse("/t/action/?query=0815")

            response.assertStatus(ResponseStatus.Ok)
            assertEquals("815", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testActionWithBody(engine: ServerEngine, mode: ExecutionMode) = test {
        getRunner(engine, mode).use { runner ->
            val request = runner.getRequest("/t/action/", "POST")
                .method("POST", BodyPublishers.ofString("{ \"field\": \"FieldData\" }"))
                .header("Content-Type", "application/json")

            val response = runner.getResponse(request)

            response.assertStatus(ResponseStatus.Ok)
            assertEquals("FieldData", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testActionWithParameter(engine: ServerEngine, mode: ExecutionMode) = test {
        getRunner(engine, mode).use { runner ->
            val response = runner.getResponse("/t/simple-action/4711/")

            response.assertStatus(ResponseStatus.Ok)
            assertEquals("4711", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testActionWithBadParameter(engine: ServerEngine, mode: ExecutionMode) = test {
        getRunner(engine, mode).use { runner ->
            val response = runner.getResponse("/t/simple-action/string/")

            response.assertStatus(ResponseStatus.BadRequest)
        }
    }

    @MultiEngineFrameworkTest
    fun testActionWithMixedParameters(engine: ServerEngine, mode: ExecutionMode) = test {
        getRunner(engine, mode).use { runner ->
            val response = runner.getResponse("/t/complex-action/1/2/?three=3")

            response.assertStatus(ResponseStatus.Ok)
            assertEquals("6", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testActionWithNoResult(engine: ServerEngine, mode: ExecutionMode) = test {
        getRunner(engine, mode).use { runner ->
            val response = runner.getResponse("/t/void/")

            response.assertStatus(ResponseStatus.NoContent)
        }
    }

    @MultiEngineFrameworkTest
    fun testNonExistingAction(engine: ServerEngine, mode: ExecutionMode) = test {
        getRunner(engine, mode).use { runner ->
            val response = runner.getResponse("/t/nope/")

            response.assertStatus(ResponseStatus.NotFound)
        }
    }

    @MultiEngineFrameworkTest
    fun testHypenCasing(engine: ServerEngine, mode: ExecutionMode) = test {
        getRunner(engine, mode).use { runner ->
            val response = runner.getResponse("/t/hypen-casing-99/")

            response.assertStatus(ResponseStatus.Ok)
            assertEquals("OK", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testIndexController(engine: ServerEngine, mode: ExecutionMode) = test {
        TestHost.run(Layout.create().indexController<TestController>(mode = mode), engine = engine).use { runner ->
            val response = runner.getResponse("/simple-action/4711/")

            response.assertStatus(ResponseStatus.Ok)
            assertEquals("4711", response.getContent())
        }
    }

    // endregion

}
