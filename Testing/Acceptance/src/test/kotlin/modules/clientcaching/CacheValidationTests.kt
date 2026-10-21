package org.genhttp.testing.acceptance.modules.clientcaching

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.clientcaching.ClientCache
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.fromString
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.FunctionalHandler
import org.genhttp.testing.getContentHeader
import org.genhttp.testing.getHeader

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

class CacheValidationTests {

    @MultiEngineTest
    fun testETagIsGenerated(engine: ServerEngine) = test {
        TestHost.run(Content.from(Resource.fromString("Hello World!")), engine = engine).use { runner ->
            val response = runner.getResponse()

            val eTag = response.getHeader("ETag")

            assertNotNull(eTag)

            AssertX.startsWith("\"", eTag)
            AssertX.endsWith("\"", eTag)
        }
    }

    @MultiEngineTest
    fun testServerReturnsUnmodified(engine: ServerEngine) = test {
        TestHost.run(Content.from(Resource.fromString("Hello World!")), engine = engine).use { runner ->
            val response = runner.getResponse()

            val eTag = response.getHeader("ETag")

            val request = runner.getRequest()

            request.header("If-None-Match", eTag!!)

            val cached = runner.getResponse(request)

            cached.assertStatus(ResponseStatus.NotModified)

            if (engine == ServerEngine.Internal) {
                assertEquals("0", cached.getContentHeader("Content-Length"))
            }
        }
    }

    @MultiEngineTest
    fun testServerReturnsModified(engine: ServerEngine) = test {
        TestHost.run(Content.from(Resource.fromString("Hello World!")), engine = engine).use { runner ->
            val request = runner.getRequest()

            request.header("If-None-Match", "\"123\"")

            val reloaded = runner.getResponse(request)

            reloaded.assertStatus(ResponseStatus.Ok)
        }
    }

    @MultiEngineTest
    fun testNoContentNoEtag(engine: ServerEngine) = test {
        val noContent = FunctionalHandler { r ->
            r.respond().status(ResponseStatus.NoContent).build()
        }

        TestHost.run(noContent, engine = engine).use { runner ->
            val response = runner.getResponse()

            assertFalse(response.headers().firstValue("ETag").isPresent)
        }
    }

    @MultiEngineTest
    fun testOtherMethodNoETag(engine: ServerEngine) = test {
        TestHost.run(Content.from(Resource.fromString("Hello World!")), engine = engine).use { runner ->
            val request = runner.getRequest(method = "DELETE")

            val response = runner.getResponse(request)

            assertFalse(response.headers().firstValue("ETag").isPresent)
        }
    }

    @MultiEngineTest
    fun testChainedValidationSingleETag(engine: ServerEngine) = test {
        val content = Content.from(Resource.fromString("Hello World!"))
            .add(ClientCache.validation())

        TestHost.run(content, engine = engine).use { runner ->
            val response = runner.getResponse()

            assertEquals(1, response.headers().allValues("ETag").size)
        }
    }

    @MultiEngineTest
    fun testChainedValidationReturnsUnmodified(engine: ServerEngine) = test {
        val content = Content.from(Resource.fromString("Hello World!"))
            .add(ClientCache.validation())

        TestHost.run(content, engine = engine).use { runner ->
            val response = runner.getResponse()

            val request = runner.getRequest()

            request.header("If-None-Match", response.getHeader("ETag")!!)

            val cached = runner.getResponse(request)

            cached.assertStatus(ResponseStatus.NotModified)

            assertEquals(1, cached.headers().allValues("ETag").size)
        }
    }

}
