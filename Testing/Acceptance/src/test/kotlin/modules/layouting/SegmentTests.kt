package org.genhttp.testing.acceptance.modules.layouting

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.fromString
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.layouting.addSegments
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import kotlin.test.assertEquals

class SegmentTests {

    @MultiEngineTest
    fun testSegment(engine: ServerEngine) = test {
        val app = Layout.create()

        val section = app.addSegment("segment")

        section.index(Content.from(Resource.fromString("Hello World")))

        TestHost.run(app, engine = engine).use { host ->
            val response = host.getResponse("/segment/")

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("Hello World", response.getContent())
        }
    }

    @MultiEngineTest
    fun testMultiSegment(engine: ServerEngine) = test {
        val app = Layout.create()

        val section = app.addSegments("one", "two")

        section.index(Content.from(Resource.fromString("Hello World")))

        TestHost.run(app, engine = engine).use { host ->
            val response = host.getResponse("/one/two/")

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("Hello World", response.getContent())
        }
    }

}
