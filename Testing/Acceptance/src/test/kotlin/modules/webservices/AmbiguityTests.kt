package org.genhttp.testing.acceptance.modules.webservices

import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.from
import org.genhttp.modules.io.fromString
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.reflection.ExecutionMode
import org.genhttp.modules.webservices.ResourceMethod
import org.genhttp.modules.webservices.addService
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineFrameworkTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import kotlin.test.assertEquals

class AmbiguityTests {

    // region Tests

    @MultiEngineFrameworkTest
    fun testSpecificPreferred(engine: ServerEngine, mode: ExecutionMode) = test {
        val app = Layout.create()
            .addService<TestService>("c", mode = mode)

        TestHost.run(app, engine = engine).use { host ->
            val response = host.getResponse("/c/my.txt")

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("Specific", response.getContent())
        }
    }

    // endregion

    // region Supporting data structures

    class TestService {

        @ResourceMethod
        fun wildcard(): HandlerBuilder = Content.from(Resource.fromString("Wildcard"))

        @ResourceMethod(path = "/my.txt")
        fun specific(): String = "Specific"

    }

    // endregion

}
