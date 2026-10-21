package org.genhttp.testing.acceptance.modules.errorhandling

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.errorhandling.ErrorHandler
import org.genhttp.modules.errorhandling.ErrorMapper
import org.genhttp.modules.functional.*
import org.genhttp.modules.io.strings.StringContent
import org.genhttp.modules.layouting.Layout
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import kotlin.test.assertEquals

class CustomErrorMapperTests {

    // region Supporting data structures

    class ErrorLengthMapper : ErrorMapper<Exception> {

        override suspend fun getNotFound(request: Request, handler: Handler, acceptedFormat: ByteString?): Response? =
            request.respond()
                .status(ResponseStatus.NotFound)
                .content(StringContent("404"))
                .build()

        override suspend fun map(request: Request, handler: Handler, error: Exception, acceptedFormat: ByteString?): Response? =
            request.respond()
                .status(ResponseStatus.NotFound)
                .content(StringContent("${error.message!!.length}"))
                .build()

    }

    // endregion

    // region Tests

    @MultiEngineTest
    fun test404Mapped(engine: ServerEngine) = test {
        val test = Layout.create()
            .add(ErrorHandler.from(ErrorLengthMapper()))

        TestHost.run(test, engine = engine).use { runner ->
            val response = runner.getResponse("/")
            assertEquals("404", response.getContent())
        }
    }

    @MultiEngineTest
    fun testExceptionMapped(engine: ServerEngine) = test {
        val thrower: suspend () -> Unit = { throw Exception("Nope!") }

        val test = Layout.create()
            .add(Inline.create().get(thrower))
            .add(ErrorHandler.from(ErrorLengthMapper()))

        TestHost.run(test, engine = engine).use { runner ->
            val response = runner.getResponse("/")
            assertEquals("5", response.getContent())
        }
    }

    // endregion

}
