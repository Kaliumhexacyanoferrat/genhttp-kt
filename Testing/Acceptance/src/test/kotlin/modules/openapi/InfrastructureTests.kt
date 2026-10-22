package org.genhttp.testing.acceptance.modules.openapi

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.functional.*
import org.genhttp.modules.openapi.ApiDescription
import org.genhttp.modules.openapi.addOpenApi
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import kotlin.test.assertEquals

class InfrastructureTests {

    // region Tests

    @MultiEngineTest
    fun testContentIsPassed(engine: ServerEngine) = test {
        val api = Inline.create()
            .get("/some-path") { "Hello World" }
            .addOpenApi()

        TestHost.run(api, engine = engine).use { host ->
            val response = host.getResponse("/some-path")

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("Hello World", response.getContent())
        }
    }

    @MultiEngineTest
    fun testCaching(engine: ServerEngine) = test { assertEquals(1, runCachingTest(engine, true)) }

    @MultiEngineTest
    fun testNoCaching(engine: ServerEngine) = test { assertEquals(2, runCachingTest(engine, false)) }

    // endregion

    // region Helpers

    private suspend fun runCachingTest(engine: ServerEngine, cacheEnabled: Boolean): Int {
        var counter = 0

        val description = ApiDescription.create().caching(cacheEnabled).postProcessor { _, _ -> counter++ }

        val api = Inline.create()
            .get { "Hello World" }
            .add(description)

        TestHost.run(api, engine = engine).use { host ->
            AssertX.contains("\"openapi\"", host.getResponse("/openapi.json").getContent())
            AssertX.contains("openapi:", host.getResponse("/openapi.yaml").getContent())
        }

        return counter
    }

    // endregion

}
