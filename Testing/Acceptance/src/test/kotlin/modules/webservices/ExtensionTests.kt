package org.genhttp.testing.acceptance.modules.webservices

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.conversion.Serialization
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.reflection.ExecutionMode
import org.genhttp.modules.reflection.Injection
import org.genhttp.modules.webservices.ResourceMethod
import org.genhttp.modules.webservices.addService
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineFrameworkTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test

class ExtensionTests {

    // region Tests

    @MultiEngineFrameworkTest
    fun testConfiguration(engine: ServerEngine, mode: ExecutionMode) = test {
        val injectors = Injection.default()

        val formats = Serialization.default()

        val app = Layout.create()
            .addService<TestService>("by-type", injectors, formats, mode = mode)
            .addService("by-instance", TestService(), injectors, formats, mode = mode)

        TestHost.run(app, engine = engine).use { host ->
            val r1 = host.getResponse("/by-type")

            r1.assertStatus(ResponseStatus.Ok)

            val r2 = host.getResponse("/by-instance")

            r2.assertStatus(ResponseStatus.Ok)
        }
    }

    // endregion

    // region Supporting data structures

    open class TestService {

        @ResourceMethod
        fun doWork(): Int = 42

    }

    // endregion

}
