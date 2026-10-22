package org.genhttp.testing.acceptance.modules.dependencyinjection

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.modules.dependencyinjection.addDependentController
import org.genhttp.modules.layouting.Layout
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import kotlin.test.assertEquals

class ControllerTests {

    // region Supporting Structures

    class TestController(private val first: AwesomeService) {

        fun doWork(second: AnotherAwesomeService): String = "${first.doWork()}-${second.doWork()}"

    }

    // endregion

    // region Functionality

    @MultiEngineTest
    fun testServiceDependencyInjection(engine: ServerEngine) = test {
        val app = Layout.create()
            .addDependentController<TestController>("t")

        DependentHost.run(app, engine = engine).use { runner ->
            val response = runner.getResponse("/t/do-work/")

            assertEquals("42-24", response.getContent())
        }
    }

    // endregion

}
