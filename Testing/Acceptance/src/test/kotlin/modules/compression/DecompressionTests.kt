package org.genhttp.testing.acceptance.modules.compression

import com.aayushatharva.brotli4j.Brotli4jLoader
import com.aayushatharva.brotli4j.encoder.BrotliOutputStream
import com.github.luben.zstd.ZstdOutputStream

import org.genhttp.api.content.io.AlgorithmName
import org.genhttp.api.content.io.CompressionAlgorithm
import org.genhttp.api.content.io.CompressionLevel
import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.Priority
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.modules.compression.DecompressedContent
import org.genhttp.modules.compression.decompression
import org.genhttp.modules.io.content
import org.genhttp.modules.io.fromString
import org.genhttp.modules.io.strings.StringContent
import org.genhttp.modules.practices.defaults
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.AsyncFunctionalHandler
import org.genhttp.testing.acceptance.wrap
import org.genhttp.testing.getContent

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.util.zip.GZIPOutputStream

import kotlin.test.assertEquals

class DecompressionTests {

    @MultiEngineTest
    fun testGzipDecompression(engine: ServerEngine) = test {
        testDecompression(engine, "gzip", ::compressGzip)
    }

    @MultiEngineTest
    fun testZstandardDecompression(engine: ServerEngine) = test {
        testDecompression(engine, "zstd", ::compressZstd)
    }

    @MultiEngineTest
    fun testBrotliDecompression(engine: ServerEngine) = test {
        testDecompression(engine, "br", ::compressBrotli)
    }

    /**
     * As a developer, I expect requests without Content-Encoding to pass through unchanged.
     */
    @MultiEngineTest
    fun testNoDecompressionWithoutHeader(engine: ServerEngine) = test {
        val handler = AsyncFunctionalHandler { r ->
            val memory = r.getBody()!!.asMemory()

            val body = Charsets.UTF_8.decode(memory).toString()

            r.respond()
                .content(StringContent(body))
                .build()
        }

        TestHost(handler.wrap().build(), defaults = false, engine = engine).use { runner ->
            runner.host.decompression(DecompressedContent.default()).start()

            val request = runner.getRequest()
                .method("POST", BodyPublishers.ofString(TestContent))
                .header("Content-Type", "text/plain; charset=utf-8")

            val response = runner.getResponse(request)

            val responseBody = response.getContent()
            assertEquals(TestContent, responseBody)
        }
    }

    /**
     * As a developer, I expect requests with unknown Content-Encoding to pass through unchanged.
     */
    @MultiEngineTest
    fun testNoDecompressionWithUnknownEncoding(engine: ServerEngine) = test {
        val handler = AsyncFunctionalHandler { r ->
            val memory = r.getBody()!!.asMemory()

            val body = Charsets.UTF_8.decode(memory).toString()

            r.respond()
                .content(Resource.fromString(body).build())
                .build()
        }

        TestHost(handler.wrap().build(), defaults = false, engine = engine).use { runner ->
            runner.host.decompression(DecompressedContent.default()).start()

            val request = runner.getRequest()
                .method("POST", BodyPublishers.ofString(TestContent))
                .header("Content-Type", "text/plain; charset=utf-8")
                .header("Content-Encoding", "unknown-encoding")

            val response = runner.getResponse(request)

            val responseBody = response.getContent()
            assertEquals(TestContent, responseBody)
        }
    }

    /**
     * As a developer, I want to add custom decompression algorithms.
     */
    @MultiEngineTest
    fun testCustomDecompressionAlgorithm(engine: ServerEngine) = test {
        val handler = AsyncFunctionalHandler { r ->
            val memory = r.getBody()!!.asMemory()

            val body = Charsets.UTF_8.decode(memory).toString()

            r.respond()
                .content(Resource.fromString(body).build())
                .build()
        }

        TestHost(handler.wrap().build(), defaults = false, engine = engine).use { runner ->
            runner.host.decompression(DecompressedContent.empty().add(CustomDecompression())).start()

            // "custom" encoding just reverses the string
            val reversed = TestContent.reversed()

            val request = runner.getRequest()
                .method("POST", BodyPublishers.ofString(reversed))
                .header("Content-Type", "text/plain; charset=utf-8")
                .header("Content-Encoding", "custom")

            val response = runner.getResponse(request)

            val responseBody = response.getContent()
            assertEquals(TestContent, responseBody)
        }
    }

    /**
     * As a developer, I expect decompression to work with the defaults() method.
     */
    @MultiEngineTest
    fun testDecompressionViaDefaults(engine: ServerEngine) = test {
        val handler = AsyncFunctionalHandler { r ->
            val memory = r.getBody()!!.asMemory()

            val body = Charsets.UTF_8.decode(memory).toString()

            r.respond()
                .content(Resource.fromString(body).build())
                .build()
        }

        TestHost(handler.wrap().build(), defaults = false, engine = engine).use { runner ->
            runner.host.defaults(compression = false, decompression = true, secureUpgrade = false, strictTransport = false, clientCaching = false).start()

            val request = getRequestWithCompressedPayload(runner)

            val response = runner.getResponse(request)

            val responseBody = response.getContent()
            assertEquals(TestContent, responseBody)
        }
    }

    // region Helpers

    private fun getRequestWithCompressedPayload(runner: TestHost, path: String? = null): HttpRequest.Builder {
        val compressedContent = compressGzip(TestContent)

        return runner.getRequest(path, "POST")
            .method("POST", BodyPublishers.ofByteArray(compressedContent))
            .header("Content-Type", "text/plain")
            .header("Content-Encoding", "gzip")
    }

    private suspend fun testDecompression(engine: ServerEngine, encoding: String, compressor: (String) -> ByteArray) {
        val handler = AsyncFunctionalHandler { r ->
            val memory = r.getBody()!!.asMemory()

            val body = Charsets.UTF_8.decode(memory).toString()

            r.respond()
                .content(Resource.fromString(body).build())
                .build()
        }

        TestHost(handler.wrap().build(), defaults = false, engine = engine).use { runner ->
            runner.host.decompression(DecompressedContent.default()).start()

            val compressedContent = compressor(TestContent)

            val request = runner.getRequest()
                .method("POST", BodyPublishers.ofByteArray(compressedContent))
                .header("Content-Type", "text/plain")
                .header("Content-Encoding", encoding)

            val response = runner.getResponse(request)

            val responseBody = response.getContent()
            assertEquals(TestContent, responseBody)
        }
    }

    private fun compressGzip(content: String): ByteArray {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).use { it.write(bytes, 0, bytes.size) }
        return output.toByteArray()
    }

    private fun compressBrotli(content: String): ByteArray {
        Brotli4jLoader.ensureAvailability()

        val bytes = content.toByteArray(Charsets.UTF_8)
        val output = ByteArrayOutputStream()
        BrotliOutputStream(output).use { it.write(bytes, 0, bytes.size) }
        return output.toByteArray()
    }

    private fun compressZstd(content: String): ByteArray {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val output = ByteArrayOutputStream()
        ZstdOutputStream(output).use { it.write(bytes, 0, bytes.size) }
        return output.toByteArray()
    }

    // endregion

    // region Custom Algorithm

    private class CustomDecompression : CompressionAlgorithm {

        override val name: AlgorithmName = AlgorithmName("custom")

        override val fileExtension: String = "custom"

        override val priority: Priority = Priority.Low

        override fun compress(content: ResponseContent, level: CompressionLevel): ResponseContent = throw NotImplementedError()

        override fun decompress(content: InputStream): InputStream {
            // Simple "decompression" that reverses the bytes
            val reversed = content.reader().use { it.readText() }.reversed()
            return ByteArrayInputStream(reversed.toByteArray(Charsets.UTF_8))
        }

    }

    // endregion

    companion object {

        private const val TestContent = "Hello, this is test content that will be compressed!"

    }

}
