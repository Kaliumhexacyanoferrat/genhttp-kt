package org.genhttp.testing.acceptance.modules.controllers

import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.controllers.ControllerAction
import org.genhttp.modules.controllers.FromPath
import org.genhttp.modules.controllers.addController
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

class SeoTests {

    // region Tests

    /**
     * As the developer of a web application, I don't want the MCV framework to generate duplicate content
     * by accepting upper case letters in action names.
     */
    @MultiEngineFrameworkTest
    fun testActionCasingMatters(engine: ServerEngine, mode: ExecutionMode) = test {
        getRunner(engine, mode).use { runner ->
            val response = runner.getResponse("/t/Action/")

            response.assertStatus(ResponseStatus.NotFound)
        }
    }

    // endregion

    // region Helpers

    private suspend fun getRunner(engine: ServerEngine, mode: ExecutionMode): TestHost =
        TestHost.run(Layout.create().addController<TestController>("t", mode = mode), engine = engine)

    // endregion

    // region Supporting data structures

    class TestController {

        fun action(): HandlerBuilder = Content.from(Resource.fromString("Action"))

        @ControllerAction(Method.Delete)
        fun action(@FromPath id: Int): HandlerBuilder = Content.from(Resource.fromString(id.toString()))

    }

    // endregion

}
