package org.genhttp.testing.acceptance.modules.errorhandling

import org.genhttp.api.content.ProviderException
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.errorhandling.ErrorHandler
import org.genhttp.modules.functional.*
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test

class HtmlErrorMapperTest {

    @MultiEngineTest
    fun testNotFound(engine: ServerEngine) = test {
        TestHost.run(Inline.create().add(ErrorHandler.html()), engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.NotFound)
        }
    }

    @MultiEngineTest
    fun testGeneralError(engine: ServerEngine) = test {
        val handler = Inline.create()
            .get { doThrow(Exception("Oops")) }
            .add(ErrorHandler.html())

        TestHost.run(handler, engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.InternalServerError)
        }
    }

    @MultiEngineTest
    fun testProviderError(engine: ServerEngine) = test {
        val handler = Inline.create()
            .get { doThrow(ProviderException(ResponseStatus.Locked, "Locked up!")) }
            .add(ErrorHandler.html())

        TestHost.run(handler, engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.Locked)
        }
    }

    @MultiEngineTest
    fun testNoTraceInProduction(engine: ServerEngine) = test {
        val handler = Inline.create()
            .get { doThrow(Exception("Oops")) }
            .add(ErrorHandler.html())

        TestHost.run(handler, development = false, engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.InternalServerError)
        }
    }

    private fun doThrow(e: Exception) {
        throw e
    }

}
