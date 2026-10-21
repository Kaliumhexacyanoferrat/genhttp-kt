package org.genhttp.testing.acceptance.modules.clientcaching

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.fromString
import org.genhttp.modules.layouting.Layout
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.getETag
import org.genhttp.testing.acceptance.test

import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ChecksumTests {

    @MultiEngineTest
    fun testSameErrorSameChecksum(engine: ServerEngine) = test {
        TestHost.run(Layout.create(), engine = engine).use { runner ->
            val resp1 = runner.getResponse()
            val resp2 = runner.getResponse()

            assertNotNull(resp1.getETag())

            assertEquals(resp1.getETag(), resp2.getETag())
        }
    }

    @MultiEngineTest
    fun testSameContentSameChecksum(engine: ServerEngine) = test {
        TestHost.run(Content.from(Resource.fromString("Hello World!")), engine = engine).use { runner ->
            val resp1 = runner.getResponse()
            val resp2 = runner.getResponse()

            assertNotNull(resp1.getETag())

            assertEquals(resp1.getETag(), resp2.getETag())
        }
    }

}
