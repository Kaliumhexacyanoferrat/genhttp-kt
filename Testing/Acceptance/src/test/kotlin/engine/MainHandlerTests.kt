package org.genhttp.testing.acceptance.engine

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.modules.io.strings.StringContent
import org.genhttp.modules.layouting.Layout
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import kotlin.test.assertEquals

class MainHandlerTests {

    // region Supporting data structures

    class PreparationHandler : Handler {

        private var prepared = false

        override suspend fun prepare(server: Server) {
            prepared = true
        }

        override suspend fun handle(request: Request): Response? =
            request.respond()
                .content(StringContent(if (prepared) "prepared" else "not prepared"))
                .build()

    }

    // endregion

    @MultiEngineTest
    fun testHandlerPreparation(engine: ServerEngine) = test {
        TestHost.run(PreparationHandler(), engine = engine).use { host ->
            val response = host.getResponse()

            assertEquals("prepared", response.getContent())
        }
    }

    @MultiEngineTest
    fun testLayoutHandlerPreparation(engine: ServerEngine) = test {
        val app = Layout.create().add("sub", PreparationHandler())

        TestHost.run(app, engine = engine).use { host ->
            val response = host.getResponse("/sub/")

            assertEquals("prepared", response.getContent())
        }
    }

}
