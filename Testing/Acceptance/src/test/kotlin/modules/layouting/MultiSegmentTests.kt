package org.genhttp.testing.acceptance.modules.layouting

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.fromString
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.layouting.add
import org.genhttp.modules.redirects.Redirect
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MultiSegmentTests {

    @MultiEngineTest
    fun testRegular(engine: ServerEngine) = test {
        val app = Layout.create()
            .add(arrayOf("api", "v1"), Content.from(Resource.fromString("Hello API!")))

        TestHost.run(app, engine = engine).use { host ->
            val response = host.getResponse("/api/v1/")

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("Hello API!", response.getContent())
        }
    }

    @MultiEngineTest
    fun testExisting(engine: ServerEngine) = test {
        val app = Layout.create()
            .add(arrayOf("api", "v1", "first"), Content.from(Resource.fromString("First")))
            .add(arrayOf("api", "v1", "second"), Content.from(Resource.fromString("Second")))

        TestHost.run(app, engine = engine).use { host ->
            val response = host.getResponse("/api/v1/second")

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("Second", response.getContent())
        }
    }

    @MultiEngineTest
    fun testNoSegments(engine: ServerEngine) = test {
        val app = Layout.create().add(arrayOf<String>(), Content.from(Resource.fromString("Content")))

        TestHost.run(app, engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("Content", response.getContent())
        }
    }

    @Test
    fun testSegmentOccupied() {
        assertFailsWith<IllegalStateException> {
            Layout.create()
                .add("api", Redirect.to("https://example.com"))
                .add(arrayOf("api", "v1"), Content.from(Resource.fromString("API")))
        }
    }

    @MultiEngineTest
    fun testEmpty(engine: ServerEngine) = test {
        val app = Layout.create()
            .add(arrayOf<String>(), Content.from(Resource.fromString("Hello Empty!")))

        TestHost.run(app, engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("Hello Empty!", response.getContent())
        }
    }

}
