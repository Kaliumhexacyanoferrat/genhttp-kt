package org.genhttp.testing.acceptance.modules.security

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.fromString
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.security.harden
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getHeader

import kotlin.test.assertEquals

class ExtensionTests {

    @MultiEngineTest
    fun serverCanBeHardened(engine: ServerEngine) = test {
        TestHost(Layout.create().build(), engine = engine).use { runner ->
            runner.host.handler(Content.from(Resource.fromString("Hello Eve!")))
                .harden()
                .start()

            val response = runner.getResponse()

            assertEquals("nosniff", response.getHeader("X-Content-Type-Options"))
        }
    }

}
