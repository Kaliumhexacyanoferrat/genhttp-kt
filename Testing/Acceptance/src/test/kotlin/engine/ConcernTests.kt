package org.genhttp.testing.acceptance.engine

import org.genhttp.api.content.Concern
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.functional.*
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import kotlin.test.assertEquals

class ConcernTests {

    // region Supporting data structures

    private class DynamicConcern(override val content: Handler, val logic: () -> Unit) : Concern {

        override suspend fun prepare(server: Server) = content.prepare(server)

        override suspend fun handle(request: Request): Response? {
            logic()

            return content.handle(request)
        }

    }

    private class DynamicConcernBuilder(private val logic: () -> Unit) : ConcernBuilder {

        override fun build(content: Handler): Concern = DynamicConcern(content, logic)

    }

    // endregion

    // region Tests

    @MultiEngineTest
    fun testConcernOrder(engine: ServerEngine) = test {
        var i = 0

        val outer = DynamicConcernBuilder { assertEquals(0, i++) }
        val middle = DynamicConcernBuilder { assertEquals(1, i++) }
        val inner = DynamicConcernBuilder { assertEquals(2, i++) }

        val content = Inline.create()
            .get { i }
            .add(inner)
            .add(middle)
            .add(outer)

        TestHost.run(content, engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("3", response.getContent())
        }
    }

    // endregion

}
