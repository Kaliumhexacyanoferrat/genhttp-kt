package org.genhttp.testing.acceptance.modules.io

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.fromString
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import kotlin.test.assertEquals

class ContentTests {

    @MultiEngineTest
    fun testContent(engine: ServerEngine) = test {
        TestHost.run(Content.from(Resource.fromString("Hello World!")), engine = engine).use { runner ->
            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.Ok)
            assertEquals("Hello World!", response.getContent())
        }
    }

    @MultiEngineTest
    fun testContentIgnoresRouting(engine: ServerEngine) = test {
        TestHost.run(Content.from(Resource.fromString("Hello World!")), engine = engine).use { runner ->
            val response = runner.getResponse("/some/path")

            response.assertStatus(ResponseStatus.Ok)
        }
    }

}
