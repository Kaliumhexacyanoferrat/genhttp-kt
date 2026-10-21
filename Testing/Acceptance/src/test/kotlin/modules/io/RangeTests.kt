package org.genhttp.testing.acceptance.modules.io

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.files.Asset
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.addRangeSupport
import org.genhttp.modules.io.fromString
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent
import org.genhttp.testing.getContentHeader
import org.genhttp.testing.getHeader

import java.net.http.HttpResponse

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RangeTests {

    private val data = "0123456789"

    @MultiEngineTest
    fun testRangesAreOptional(engine: ServerEngine) = test {
        val response = getResponse(engine, null)

        response.assertStatus(ResponseStatus.Ok)
        assertEquals(data, response.getContent())
    }

    @MultiEngineTest
    fun testFullRangeIsSatisfied(engine: ServerEngine) = test {
        val response = getResponse(engine, "bytes=1-8")

        response.assertStatus(ResponseStatus.PartialContent)
        assertEquals("12345678", response.getContent())
        assertEquals("bytes 1-8/10", response.getContentHeader("Content-Range"))
    }

    @MultiEngineTest
    fun testRangeFromStartIsSatisfied(engine: ServerEngine) = test {
        val response = getResponse(engine, "bytes=4-")

        response.assertStatus(ResponseStatus.PartialContent)
        assertEquals("456789", response.getContent())
        assertEquals("bytes 4-9/10", response.getContentHeader("Content-Range"))
    }

    @MultiEngineTest
    fun testRangeFromEndIsSatisfied(engine: ServerEngine) = test {
        val response = getResponse(engine, "bytes=-4")

        response.assertStatus(ResponseStatus.PartialContent)
        assertEquals("6789", response.getContent())
        assertEquals("bytes 6-9/10", response.getContentHeader("Content-Range"))
    }

    @MultiEngineTest
    fun testSingleRangeIsSatisfied(engine: ServerEngine) = test {
        val response = getResponse(engine, "bytes=1-1")

        response.assertStatus(ResponseStatus.PartialContent)
        assertEquals("1", response.getContent())
        assertEquals("bytes 1-1/10", response.getContentHeader("Content-Range"))
    }

    @MultiEngineTest
    fun testFullRangeNotSatisfied(engine: ServerEngine) = test {
        val response = getResponse(engine, "bytes=9-13")

        response.assertStatus(ResponseStatus.RequestedRangeNotSatisfiable)
        assertEquals("bytes */10", response.getContentHeader("Content-Range"))
    }

    @MultiEngineTest
    fun testRangeFromStartNotSatisfied(engine: ServerEngine) = test {
        val response = getResponse(engine, "bytes=12-")

        response.assertStatus(ResponseStatus.RequestedRangeNotSatisfiable)
        assertEquals("bytes */10", response.getContentHeader("Content-Range"))
    }

    @MultiEngineTest
    fun testRangeFromEndNotSatisfied(engine: ServerEngine) = test {
        val response = getResponse(engine, "bytes=-12")

        response.assertStatus(ResponseStatus.RequestedRangeNotSatisfiable)
        assertEquals("bytes */10", response.getContentHeader("Content-Range"))
    }

    @MultiEngineTest
    fun testMultipleRangesNotSatisfied(engine: ServerEngine) = test {
        val response = getResponse(engine, "bytes=1-2,3-4")

        response.assertStatus(ResponseStatus.RequestedRangeNotSatisfiable)
        assertEquals("bytes */10", response.getContentHeader("Content-Range"))
    }

    @MultiEngineTest
    fun testOneBasedIndexDoesNotWork(engine: ServerEngine) = test {
        val response = getResponse(engine, "bytes=1-10")

        response.assertStatus(ResponseStatus.RequestedRangeNotSatisfiable)
        assertEquals("bytes */10", response.getContentHeader("Content-Range"))
    }

    @MultiEngineTest
    fun testHeadRequest(engine: ServerEngine) = test {
        val response = getResponse(engine, "bytes=1-8", "HEAD")

        response.assertStatus(ResponseStatus.PartialContent)

        assertEquals("bytes 1-8/10", response.getContentHeader("Content-Range"))
        assertEquals("8", response.getContentHeader("Content-Length"))

        assertEquals("bytes", response.getHeader("Accept-Ranges"))
    }

    @MultiEngineTest
    fun testRangesIgnoredOnPostRequests(engine: ServerEngine) = test {
        val response = getResponse(engine, "bytes=1-8", "POST")

        response.assertStatus(ResponseStatus.Ok)
        assertEquals(data, response.getContent())
    }

    @MultiEngineTest
    fun testRangesAreTaggedDifferently(engine: ServerEngine) = test {
        val withRange = getResponse(engine, "bytes=1-8")
        val withoutRange = getResponse(engine, null)

        assertNotEquals(withRange.getHeader("ETag"), withoutRange.getHeader("ETag"))
    }

    @MultiEngineTest
    fun testAddSupportForSingleFile(engine: ServerEngine) = test {
        val download = Asset.from(Resource.fromString("Hello World!"))
            .addRangeSupport()

        TestHost.run(download).use { runner ->
            val response = runner.getResponse()

            assertEquals("bytes", response.getHeader("Accept-Ranges"))
        }
    }

    private suspend fun getResponse(engine: ServerEngine, requestedRange: String?, method: String? = null): HttpResponse<ByteArray> =
        getRunner(engine).use { runner ->
            val request = runner.getRequest(method = method ?: "GET")

            if (requestedRange != null) {
                request.header("Range", requestedRange)
            }

            runner.getResponse(request)
        }

    private suspend fun getRunner(engine: ServerEngine): TestHost {
        val content = Content.from(Resource.fromString(data))

        content.addRangeSupport()

        return TestHost.run(content, engine = engine)
    }

}
