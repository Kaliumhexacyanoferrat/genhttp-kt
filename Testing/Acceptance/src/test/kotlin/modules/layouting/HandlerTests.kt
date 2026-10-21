package org.genhttp.testing.acceptance.modules.layouting

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.fromString
import org.genhttp.modules.layouting.Layout
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import kotlin.test.assertEquals

class HandlerTests {

    @MultiEngineTest
    fun testHandlerDirect(engine: ServerEngine) = test {
        val layout = Layout.create()
            .add("section", Content.from(Resource.fromString("Hello World")).build())

        TestHost.run(layout, engine = engine).use { host ->
            val response = host.getResponse("/section/")

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("Hello World", response.getContent())
        }
    }

    @MultiEngineTest
    fun testIndexDirect(engine: ServerEngine) = test {
        val layout = Layout.create()
            .index(Content.from(Resource.fromString("Hello World")).build())

        TestHost.run(layout, engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("Hello World", response.getContent())
        }
    }

    @MultiEngineTest
    fun testFallbackDirect(engine: ServerEngine) = test {
        val layout = Layout.create()
            .add(Content.from(Resource.fromString("Hello World")).build())

        TestHost.run(layout, engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("Hello World", response.getContent())
        }
    }

}
