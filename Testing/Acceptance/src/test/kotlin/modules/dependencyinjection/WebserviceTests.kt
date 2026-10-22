package org.genhttp.testing.acceptance.modules.dependencyinjection

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.modules.conversion.Formatting
import org.genhttp.modules.conversion.Serialization
import org.genhttp.modules.dependencyinjection.addDependentService
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.reflection.Injection
import org.genhttp.modules.webservices.ResourceMethod
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import org.junit.jupiter.api.Test

import kotlin.test.assertEquals

class WebserviceTests {

    // region Supporting Structures

    class TestService(private val first: AwesomeService) {

        @ResourceMethod
        fun doWork(second: AnotherAwesomeService): String = "${first.doWork()}-${second.doWork()}"

    }

    // endregion

    // region Functionality

    @MultiEngineTest
    fun testServiceDependencyInjection(engine: ServerEngine) = test {
        val app = Layout.create()
            .addDependentService<TestService>("service")

        DependentHost.run(app, engine = engine).use { runner ->
            val response = runner.getResponse("/service")

            assertEquals("42-24", response.getContent())
        }
    }

    @Test
    fun testCustomization() = test {
        val app = Layout.create()
            .addDependentService<TestService>("s", Injection.default(), Serialization.default(), Formatting.default())

        DependentHost.run(app).use { runner ->
            val response = runner.getResponse("/s")

            assertEquals(200, response.statusCode())
        }
    }

    // endregion

}
