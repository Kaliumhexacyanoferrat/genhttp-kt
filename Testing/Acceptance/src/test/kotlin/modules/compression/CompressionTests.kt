package org.genhttp.testing.acceptance.modules.compression

import org.genhttp.api.content.io.AlgorithmName
import org.genhttp.api.content.io.CompressionAlgorithm
import org.genhttp.api.content.io.CompressionLevel
import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.Priority
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseSink
import org.genhttp.modules.compression.CompressedContent
import org.genhttp.modules.compression.compression
import org.genhttp.modules.compression.providers.CompressedResponseContent
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.fromString
import org.genhttp.modules.io.streaming.ResourceContent
import org.genhttp.modules.io.strings.StringContent
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.layouting.provider.LayoutBuilder
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.FunctionalHandler
import org.genhttp.testing.acceptance.wrap
import org.genhttp.testing.getContentHeader
import org.genhttp.testing.getHeader

import java.io.InputStream
import java.net.http.HttpResponse
import java.nio.ByteBuffer

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompressionTests {

    /**
     * As a developer, I expect responses to be compressed out of the box.
     */
    @MultiEngineTest
    fun testCompression(engine: ServerEngine) = test {
        TestHost.run(createLargeContentHandler().build(), engine = engine).use { runner ->
            val request = runner.getRequest()
            request.header("Accept-Encoding", "gzip, br")

            val response = runner.getResponse(request)

            assertEquals("br", response.getContentHeader("Content-Encoding"))
        }
    }

    /**
     * As a browser, I expect only supported compression algorithms to be used
     * to generate my response.
     */
    @MultiEngineTest
    fun testSpecificAlgorithms(engine: ServerEngine) = test {
        val list = listOf("gzip", "br", "zstd")

        for (algorithm in list) {
            TestHost.run(createLargeContentHandler(), engine = engine).use { runner ->
                val request = runner.getRequest()
                request.header("Accept-Encoding", algorithm)

                val response = runner.getResponse(request)

                assertEquals(algorithm, response.getContentHeader("Content-Encoding"))
            }
        }
    }

    /**
     * As a developer, I want to be able to disable compression.
     */
    @MultiEngineTest
    fun testCompressionDisabled(engine: ServerEngine) = test {
        TestHost.run(Layout.create(), false, engine = engine).use { runner ->
            val response = runner.getResponse()

            AssertX.isNullOrEmpty(response.getContentHeader("Content-Encoding"))
        }
    }

    /**
     * As a developer, I want to be able to add custom compression algorithms.
     */
    @MultiEngineTest
    fun testCustomCompression(engine: ServerEngine) = test {
        TestHost(createLargeContentHandler().build(), defaults = false, engine = engine).use { runner ->
            runner.host.compression(CompressedContent.default().add(CustomAlgorithm()).level(CompressionLevel.Optimal)).start()

            val request = runner.getRequest()
            request.header("Accept-Encoding", "custom")

            val response = runner.getResponse(request)

            assertEquals("custom", response.getContentHeader("Content-Encoding"))
        }
    }

    /**
     * As a developer, I want already compressed content not to be compressed again.
     */
    @MultiEngineTest
    fun testNoAdditionalCompression(engine: ServerEngine) = test {
        val image = Resource.fromString("Image!").type(ContentType.ImageJpeg)

        TestHost.run(Layout.create().add("uncompressed", Content.from(image)), engine = engine).use { runner ->
            val response = runner.getResponse("/uncompressed")

            AssertX.isNullOrEmpty(response.getContentHeader("Content-Encoding"))
        }
    }

    @MultiEngineTest
    fun testVariyHeaderAdded(engine: ServerEngine) = test {
        TestHost.run(createLargeContentHandler(), engine = engine).use { runner ->
            val request = runner.getRequest()
            request.header("Accept-Encoding", "gzip")

            val response = runner.getResponse(request)

            assertEquals("Accept-Encoding", response.getHeader("Vary"))
        }
    }

    @MultiEngineTest
    fun testVaryHeaderExtendedAdded(engine: ServerEngine) = test {
        val handler = FunctionalHandler { r ->
            r.respond()
                .header("Vary", "Host")
                .content(ResourceContent(Resource.fromString(createLargeString(500)).type(ContentType.TextHtml).build()))
                .build()
        }

        TestHost.run(handler.wrap(), engine = engine).use { runner ->
            val request = runner.getRequest()
            request.header("Accept-Encoding", "gzip")

            val response = runner.getResponse(request)

            val vary = response.getVary()

            assertTrue(vary.contains("Host"))
            assertTrue(vary.contains("Accept-Encoding"))
        }
    }

    @MultiEngineTest
    fun testContentType(engine: ServerEngine) = test {
        val handler = FunctionalHandler { r ->
            r.respond()
                .content(ResourceContent(Resource.fromString(createLargeString(500)).type(ContentType("application/json; charset=utf-8")).build()))
                .build()
        }

        TestHost.run(handler.wrap(), engine = engine).use { runner ->
            val request = runner.getRequest()
            request.header("Accept-Encoding", "gzip, deflate, br")

            val response = runner.getResponse(request)

            assertEquals("br", response.getContentHeader("Content-Encoding"))
        }
    }

    /**
     * As a developer, I want content smaller than a threshold not to be compressed to avoid unnecessary overhead.
     */
    @MultiEngineTest
    fun testCompressionThreshold_SmallContent_NotCompressed(engine: ServerEngine) = test {
        // Creating content smaller than the default threshold (256 bytes)
        val smallContent = createLargeString(100)

        val handler = FunctionalHandler { r ->
            r.respond()
                .content(ResourceContent(Resource.fromString(smallContent).type(ContentType.TextHtml).build()))
                .build()
        }

        TestHost.run(handler.wrap(), engine = engine).use { runner ->
            val request = runner.getRequest()
            request.header("Accept-Encoding", "gzip, br")

            val response = runner.getResponse(request)

            // Content should not be compressed as it's below the threshold
            AssertX.isNullOrEmpty(response.getContentHeader("Content-Encoding"))
        }
    }

    /**
     * As a developer, I want content larger than a threshold to be compressed.
     */
    @MultiEngineTest
    fun testCompressionThreshold_LargeContent_Compressed(engine: ServerEngine) = test {
        // Creating content larger than the default threshold (256 bytes)
        val largeContent = createLargeString(500)

        val handler = FunctionalHandler { r ->
            r.respond()
                .content(ResourceContent(Resource.fromString(largeContent).type(ContentType.TextHtml).build()))
                .build()
        }

        TestHost.run(handler.wrap(), engine = engine).use { runner ->
            val request = runner.getRequest()
            request.header("Accept-Encoding", "gzip, br")

            val response = runner.getResponse(request)

            // Content should be compressed as it's above the threshold
            assertFalse(response.getContentHeader("Content-Encoding").isNullOrEmpty())
            assertEquals("br", response.getContentHeader("Content-Encoding"))
        }
    }

    /**
     * As a developer, I want content exactly at the threshold to be compressed (>= check).
     */
    @MultiEngineTest
    fun testCompressionThreshold_ExactThreshold_Compressed(engine: ServerEngine) = test {
        // Creating content exactly at the default threshold (256 bytes)
        val exactContent = createLargeString(256)

        val handler = FunctionalHandler { r ->
            r.respond()
                .content(ResourceContent(Resource.fromString(exactContent).type(ContentType.TextHtml).build()))
                .build()
        }

        TestHost.run(handler.wrap(), engine = engine).use { runner ->
            val request = runner.getRequest()
            request.header("Accept-Encoding", "gzip, br")

            val response = runner.getResponse(request)

            // Content should be compressed as it's exactly at the threshold (>= check)
            assertFalse(response.getContentHeader("Content-Encoding").isNullOrEmpty())
            assertEquals("br", response.getContentHeader("Content-Encoding"))
        }
    }

    /**
     * As a developer, I want to be able to configure a custom minimum size threshold.
     */
    @MultiEngineTest
    fun testCompressionThreshold_CustomThreshold(engine: ServerEngine) = test {
        // Creating content well below the threshold to ensure it's not compressed
        val smallContent = createLargeString(100)

        val handler = Content.from(Resource.fromString(smallContent).type(ContentType.TextHtml))

        TestHost(handler.build(), engine = engine).use { runner ->
            // Set custom threshold to 500 bytes - content should NOT be compressed
            runner.host.compression(CompressedContent.default().minimumSize(500)).start()

            val request = runner.getRequest()
            request.header("Accept-Encoding", "gzip, br")

            val response = runner.getResponse(request)

            // Content should not be compressed as it's below the custom threshold (500 bytes)
            AssertX.isNullOrEmpty(response.getContentHeader("Content-Encoding"))
        }
    }

    /**
     * As a developer, I want content with unknown length to always be compressed if suitable.
     */
    @MultiEngineTest
    fun testCompressionThreshold_UnknownLength_AlwaysCompressed(engine: ServerEngine) = test {
        // Creating a custom content provider that returns null for length
        val handler = FunctionalHandler { r ->
            val wrappedContent = UnknownLengthContent(StringContent("Small content"))

            r.respond()
                .content(wrappedContent)
                .build()
        }

        TestHost.run(handler.wrap(), engine = engine).use { runner ->
            val request = runner.getRequest()
            request.header("Accept-Encoding", "gzip, br")

            val response = runner.getResponse(request)

            // Content with unknown length should always be compressed if suitable
            assertFalse(response.getContentHeader("Content-Encoding").isNullOrEmpty())
            assertEquals("br", response.getContentHeader("Content-Encoding"))
        }
    }

    /**
     * As a developer, I want to be able to chain MinimumSize with Add and Level methods.
     */
    @MultiEngineTest
    fun testCompressionThreshold_MethodChaining(engine: ServerEngine) = test {
        // Verifying that the example usage pattern works: CompressedContent.default().minimumSize(512)
        val smallContent = createLargeString(100)

        val handler = Content.from(Resource.fromString(smallContent).type(ContentType.TextHtml))

        TestHost(handler.build(), engine = engine).use { runner ->
            runner.host.compression(
                CompressedContent.default()
                    .minimumSize(512)
                    .add(CustomAlgorithm())
                    .level(CompressionLevel.Optimal)
            ).start()

            val request = runner.getRequest()
            request.header("Accept-Encoding", "custom, gzip, br")

            val response = runner.getResponse(request)

            // Content should not be compressed as it's below the threshold (100 < 512)
            AssertX.isNullOrEmpty(response.getContentHeader("Content-Encoding"))
        }
    }

    /**
     * As a developer, I want to be able to disable the threshold by setting it to null.
     */
    @MultiEngineTest
    fun testCompressionThreshold_Disabled_AllContentCompressed(engine: ServerEngine) = test {
        // Creating very small content
        val smallContent = createLargeString(10) // 10 bytes when UTF-8 encoded

        val handler = FunctionalHandler { r ->
            r.respond()
                .content(ResourceContent(Resource.fromString(smallContent).type(ContentType.TextHtml).build()))
                .build()
        }

        TestHost(handler.wrap().build(), engine = engine).use { runner ->
            // Disable threshold by setting it to null
            runner.host.compression(CompressedContent.default().minimumSize(null)).start()

            val request = runner.getRequest()
            request.header("Accept-Encoding", "gzip, br")

            val response = runner.getResponse(request)

            // Content should be compressed even though it's very small, as threshold is disabled
            assertFalse(response.getContentHeader("Content-Encoding").isNullOrEmpty())
            assertEquals("br", response.getContentHeader("Content-Encoding"))
        }
    }

    @MultiEngineTest
    fun testWeights(engine: ServerEngine) = test {
        TestHost.run(createLargeContentHandler().build(), engine = engine).use { runner ->
            val request = runner.getRequest()
            request.header("Accept-Encoding", "br;q=1, gzip;q=0.8")

            val response = runner.getResponse(request)

            assertEquals("br", response.getContentHeader("Content-Encoding"))
        }
    }

    /**
     * Returns the values of the Vary header(s) of the response (the .NET client exposes them as a parsed list).
     */
    private fun HttpResponse<*>.getVary(): List<String> =
        headers().allValues("Vary").flatMap { it.split(',') }.map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Helper class to create content with unknown length (null)
     */
    private class UnknownLengthContent(private val wrapped: ResponseContent) : ResponseContent {

        override val length: Long? = null

        override val type: ContentType? = ContentType.TextHtml

        override val encoding: ByteBuffer? = null

        override suspend fun write(sink: ResponseSink) = wrapped.write(sink)

        override suspend fun calculateChecksum(): Long? = wrapped.calculateChecksum()

    }

    private class CustomAlgorithm : CompressionAlgorithm {

        override val name: AlgorithmName = AlgorithmName("custom")

        override val fileExtension: String = "custom"

        override val priority: Priority = Priority.High

        override fun compress(content: ResponseContent, level: CompressionLevel): ResponseContent =
            CompressedResponseContent(content, { target -> target }, name)

        override fun decompress(content: InputStream): InputStream = throw NotImplementedError()

    }

    companion object {

        /**
         * Helper method to create large content that will be compressed (above default threshold of 256 bytes).
         */
        private fun createLargeContentHandler(): LayoutBuilder {
            val content = Content.from(Resource.fromString(createLargeString(500)).type(ContentType.TextHtml))

            return Layout.create()
                .index(content)
        }

        /**
         * Helper method to create a large string of specified size.
         */
        private fun createLargeString(sizeInBytes: Int): String = "A".repeat(sizeInBytes)

    }

}
