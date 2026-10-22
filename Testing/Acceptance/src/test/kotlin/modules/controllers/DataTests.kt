package org.genhttp.testing.acceptance.modules.controllers

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.controllers.ControllerAction
import org.genhttp.modules.controllers.addController
import org.genhttp.modules.conversion.Formatting
import org.genhttp.modules.conversion.Serialization
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.reflection.ExecutionMode
import org.genhttp.modules.reflection.Injection
import org.genhttp.modules.reflection.Method
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineFrameworkTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import java.time.LocalDate

import kotlin.test.assertEquals

class DataTests {

    // region Helpers

    private suspend fun getHost(engine: ServerEngine, mode: ExecutionMode): TestHost {
        val app = Layout.create()
            .addController<TestController>(
                "t",
                serializers = Serialization.default(),
                injectors = Injection.default(),
                formatters = Formatting.default(),
                mode = mode
            )

        return TestHost.run(app, engine = engine)
    }

    // endregion

    // region Controller

    open class TestController {

        @ControllerAction(Method.Get)
        fun date(date: LocalDate): LocalDate = date

    }

    // endregion

    // region Tests

    @MultiEngineFrameworkTest
    fun testDateOnly(engine: ServerEngine, mode: ExecutionMode) = test {
        getHost(engine, mode).use { host ->
            val response = host.getResponse("/t/date/?date=2024-03-11")

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("2024-03-11", response.getContent())
        }
    }

    @MultiEngineFrameworkTest
    fun testInvalidDateOnly(engine: ServerEngine, mode: ExecutionMode) = test {
        getHost(engine, mode).use { host ->
            val response = host.getResponse("/t/date/?date=ABC")

            response.assertStatus(ResponseStatus.BadRequest)
        }
    }

    // endregion

}
