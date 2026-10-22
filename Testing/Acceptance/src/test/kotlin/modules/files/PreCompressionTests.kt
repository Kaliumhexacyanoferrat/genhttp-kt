package org.genhttp.testing.acceptance.modules.files

import org.genhttp.api.content.io.ResourceTree
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.compression.algorithms.BrotliAlgorithm
import org.genhttp.modules.files.Assets
import org.genhttp.modules.io.fromClasspath
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.Chain
import org.genhttp.testing.getContent
import org.genhttp.testing.getContentHeader

import org.junit.jupiter.api.Test

import kotlin.test.assertEquals
import kotlin.test.assertTrue

open class PreCompressionTests {

    @MultiEngineTest
    fun testRegular(engine: ServerEngine) = test {
        run(engine).use { runner ->
            val request = runner.getRequest("/Resources/LargeTemplate.html")

            request.header("Accept-Encoding", "br")

            val response = runner.getResponse(request)

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("br", response.headers().allValues("Content-Encoding").first())

            assertEquals("text/html", response.getContentHeader("Content-Type"))

            val content = response.getContent()

            AssertX.doesNotContain("precompressed", content) // not automatically decompressed
        }
    }

    @MultiEngineTest
    fun testNonSupportedAlgorithm(engine: ServerEngine) = test {
        run(engine).use { runner ->
            val request = runner.getRequest("/Resources/LargeTemplate.html")

            request.header("Accept-Encoding", "gzip")

            val response = runner.getResponse(request)

            response.assertStatus(ResponseStatus.Ok)

            assertTrue(response.headers().allValues("Content-Encoding").isEmpty())
        }
    }

    @MultiEngineTest
    fun testNoCompressionRequested(engine: ServerEngine) = test {
        run(engine).use { runner ->
            val response = runner.getResponse("/Resources/LargeTemplate.html")

            response.assertStatus(ResponseStatus.Ok)

            assertTrue(response.headers().allValues("Content-Encoding").isEmpty())
        }
    }

    @MultiEngineTest
    fun testFolderNotFound(engine: ServerEngine) = test {
        run(engine).use { runner ->
            val request = runner.getRequest("/Resources/SubDirectory/")

            request.header("Accept-Encoding", "br")

            val response = runner.getResponse(request)

            response.assertStatus(ResponseStatus.NotFound)
        }
    }

    @Test
    fun testChaining() {
        val handler = Assets.from(ResourceTree.fromClasspath(""))
            .allowPrecompressed(BrotliAlgorithm())

        Chain.works(handler)
    }

    private suspend fun run(engine: ServerEngine): TestHost {
        val handler = Assets.from(ResourceTree.fromClasspath(""))
            .allowPrecompressed(arrayOf(BrotliAlgorithm()), '+')

        return TestHost.run(handler, defaults = false, engine = engine)
    }

}
