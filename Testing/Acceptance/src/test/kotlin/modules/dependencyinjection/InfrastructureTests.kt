package org.genhttp.testing.acceptance.modules.dependencyinjection

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.dependencyinjection.getServiceProvider
import org.genhttp.modules.dependencyinjection.getServiceScope
import org.genhttp.modules.functional.Inline
import org.genhttp.modules.functional.get
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test

import org.junit.jupiter.api.Test

import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class InfrastructureTests {

    @MultiEngineTest
    fun testServiceAvailability(engine: ServerEngine) = test {
        val app = Inline.create()
            .get { r: Request ->
                assertNotNull(r.getServiceProvider())
                assertNotNull(r.getServiceScope())

                Unit
            }

        DependentHost.run(app, engine = engine).use { runner ->
            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.NoContent)
        }
    }

    @Test
    fun testMeaningfulErrorIfNotConfigured() = test {
        val app = Inline.create()
            .get { r: Request ->
                assertFailsWith<IllegalStateException> { r.getServiceProvider() }
                assertFailsWith<IllegalStateException> { r.getServiceScope() }

                Unit
            }

        TestHost.run(app).use { runner ->
            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.NoContent)
        }
    }

}
