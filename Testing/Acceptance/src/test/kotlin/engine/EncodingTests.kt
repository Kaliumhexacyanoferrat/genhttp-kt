package org.genhttp.testing.acceptance.engine

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.fromString
import org.genhttp.modules.layouting.Layout
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import kotlin.test.assertEquals

class EncodingTests {

    /**
     * As a developer, I want UTF-8 to be my default encoding.
     */
    @MultiEngineTest
    fun testUtf8DefaultEncoding(engine: ServerEngine) = test {
        val layout = Layout.create().add("utf8", Content.from(Resource.fromString("From GenHTTP with ❤")))

        TestHost.run(layout, engine = engine).use { runner ->
            val response = runner.getResponse("/utf8")

            assertEquals("From GenHTTP with ❤", response.getContent())
        }
    }

}
