package org.genhttp.testing.acceptance.modules.controllers

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.controllers.Controller
import org.genhttp.modules.controllers.ControllerAction
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.reflection.ExecutionMode
import org.genhttp.modules.reflection.Method
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineFrameworkTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.Chain
import org.genhttp.testing.getContent

import org.junit.jupiter.api.Test

import kotlin.test.assertEquals

open class IntegrationTests {

    // region Supporting data structures

    open class TestController {

        @ControllerAction(Method.Get)
        fun doWork(): String = "Work done"

    }

    // endregion

    @MultiEngineFrameworkTest
    fun testInstance(engine: ServerEngine, mode: ExecutionMode) = test {
        val controller = Controller.from(TestController())
            .executionMode(mode)

        val app = Layout.create()
            .add(controller)

        TestHost.run(app, engine = engine).use { host ->
            val response = host.getResponse("/do-work/")

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("Work done", response.getContent())
        }
    }

    @Test
    fun testChaining() {
        Chain.works(Controller.from<TestController>())
    }

}
