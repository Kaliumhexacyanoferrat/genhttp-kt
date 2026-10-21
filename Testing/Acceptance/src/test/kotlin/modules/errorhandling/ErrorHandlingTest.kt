package org.genhttp.testing.acceptance.modules.errorhandling

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.FunctionalHandler
import org.genhttp.testing.acceptance.wrap
import org.genhttp.testing.getContent

class ErrorHandlingTest {

    @MultiEngineTest
    fun testGenericError(engine: ServerEngine) = test {
        // NotImplementedException is NotImplementedError in Kotlin, which is not an Exception
        val handler = FunctionalHandler { _ ->
            throw UnsupportedOperationException()
        }

        TestHost.run(handler.wrap(), engine = engine).use { runner ->
            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.InternalServerError)
        }
    }

    @MultiEngineTest
    fun testEscaping(engine: ServerEngine) = test {
        val handler = FunctionalHandler { _ ->
            throw Exception("Nah <>")
        }

        TestHost.run(handler.wrap(), engine = engine).use { runner ->
            val response = runner.getResponse()

            AssertX.doesNotContain("<>", response.getContent())
        }
    }

}
