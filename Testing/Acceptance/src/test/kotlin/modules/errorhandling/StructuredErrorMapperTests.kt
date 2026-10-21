package org.genhttp.testing.acceptance.modules.errorhandling

import org.genhttp.api.content.ProviderException
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.errorhandling.mappers.StructuredErrorMapper
import org.genhttp.modules.functional.Inline
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StructuredErrorMapperTests {

    @MultiEngineTest
    fun testNotFound(engine: ServerEngine) = test {
        TestHost.run(Inline.create(), engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.NotFound)

            val model = response.getContent<StructuredErrorMapper.ErrorModel>()

            assertEquals(ResponseStatus.NotFound, model.status)

            assertNull(model.stackTrace)
        }
    }

    @MultiEngineTest
    fun testGeneralError(engine: ServerEngine) = test {
        val handler = Inline.create()
            .get { doThrow(Exception("Oops")) }

        TestHost.run(handler, engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.InternalServerError)

            val model = response.getContent<StructuredErrorMapper.ErrorModel>()

            assertEquals(ResponseStatus.InternalServerError, model.status)

            assertNotNull(model.stackTrace)
        }
    }

    @MultiEngineTest
    fun testProviderError(engine: ServerEngine) = test {
        val handler = Inline.create()
            .get { doThrow(ProviderException(ResponseStatus.Locked, "Locked up!")) }

        TestHost.run(handler, engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.Locked)

            val model = response.getContent<StructuredErrorMapper.ErrorModel>()

            assertEquals(ResponseStatus.Locked, model.status)

            assertNotNull(model.stackTrace)
        }
    }

    @MultiEngineTest
    fun testNoTraceInProduction(engine: ServerEngine) = test {
        val handler = Inline.create()
            .get { doThrow(Exception("Oops")) }

        TestHost.run(handler, development = false, engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.InternalServerError)

            val model = response.getContent<StructuredErrorMapper.ErrorModel>()

            assertNull(model.stackTrace)
        }
    }

    private fun doThrow(e: Exception) {
        throw e
    }

}
