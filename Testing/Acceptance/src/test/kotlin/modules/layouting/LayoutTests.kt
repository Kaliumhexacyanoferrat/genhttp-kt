package org.genhttp.testing.acceptance.modules.layouting

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.authentication.authentication
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.fromString
import org.genhttp.modules.layouting.Layout
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent
import org.genhttp.testing.getHeader

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LayoutTests {

    /**
     * As a developer I can define the default route to be devlivered.
     */
    @MultiEngineTest
    fun testGetIndex(engine: ServerEngine) = test {
        val layout = Layout.create()
            .index(Content.from(Resource.fromString("Hello World!")))

        TestHost.run(layout, engine = engine).use { runner ->
            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.Ok)
            assertEquals("Hello World!", response.getContent())

            val notFound = runner.getResponse("/notfound")

            notFound.assertStatus(ResponseStatus.NotFound)
        }
    }

    /**
     * As a developer I can set a default handler to be used for requests.
     */
    @MultiEngineTest
    fun testDefaultContent(engine: ServerEngine) = test {
        val layout = Layout.create().add(Content.from(Resource.fromString("Hello World!")))

        TestHost.run(layout, engine = engine).use { runner ->
            for (path in arrayOf("/something", "/")) {
                val response = runner.getResponse(path)

                response.assertStatus(ResponseStatus.Ok)
                assertEquals("Hello World!", response.getContent())
            }
        }
    }

    /**
     * As the developer of a web application, I don't want my application
     * to produce duplicate content for missing trailing slashes.
     */
    @MultiEngineTest
    fun testRedirect(engine: ServerEngine) = test {
        val layout = Layout.create()
            .add("section", Layout.create().index(Content.from(Resource.fromString("Hello World!"))))

        TestHost.run(layout, engine = engine).use { runner ->
            val response = runner.getResponse("/section/")

            response.assertStatus(ResponseStatus.Ok)
            assertEquals("Hello World!", response.getContent())

            val redirected = runner.getResponse("/section")

            redirected.assertStatus(ResponseStatus.MovedPermanently)
            AssertX.endsWith("/section/", redirected.getHeader("Location")!!)
        }
    }

    @MultiEngineTest
    fun testMultiSegmentInName(engine: ServerEngine) = test {
        val layout = Layout.create().add("/api/v1/", Content.from(Resource.fromString("Hello World")))

        TestHost.run(layout, engine = engine).use { runner ->
            val response = runner.getResponse("/api/v1/")

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("Hello World", response.getContent())
        }
    }

    @Test
    fun testSameSegmentTwice() {
        val content = Content.from(Resource.fromString("Hello World!"))

        assertFailsWith<IllegalStateException> {
            Layout.create().add("one", content).add("one", content)
        }
    }

    @MultiEngineTest
    fun testLazyBuilding(engine: ServerEngine) = test {
        val inner = Layout.create()

        val outer = Layout.create()
            .add("inner", inner)

        // add a concern _after_ the inner handler has already been added
        // still has to take effect
        inner.authentication { _, _ -> null }

        TestHost.run(outer, engine = engine).use { host ->
            val response = host.getResponse("/inner/")

            response.assertStatus(ResponseStatus.Unauthorized)
        }
    }

}
