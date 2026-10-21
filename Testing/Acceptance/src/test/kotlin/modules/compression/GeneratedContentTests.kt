package org.genhttp.testing.acceptance.modules.compression

import com.aayushatharva.brotli4j.Brotli4jLoader
import com.aayushatharva.brotli4j.decoder.BrotliInputStream
import com.github.luben.zstd.ZstdInputStream

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.functional.Inline
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContentHeader

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.http.HttpResponse
import java.util.zip.GZIPInputStream

import kotlin.test.assertEquals

/**
 * Content generated per request (e.g. serialized JSON) has to arrive
 * completely, regardless of the compression algorithm negotiated.
 */
class GeneratedContentTests {

    data class Blob(val data: String)

    @MultiEngineTest
    fun testLargeGeneratedContentIsComplete(engine: ServerEngine) = test {
        val app = Inline.create()
            .get { Blob("x".repeat(80_000)) }

        TestHost.run(app, engine = engine).use { runner ->
            for (algorithm in getAlgorithms()) {
                val request = runner.getRequest()
                request.header("Accept-Encoding", algorithm)

                val response = runner.getResponse(request)

                response.assertStatus(ResponseStatus.Ok)

                assertEquals(algorithm, response.getContentHeader("Content-Encoding"))

                val body = read(response, algorithm)

                assertEquals("{\"data\":\"${"x".repeat(80_000)}\"}", body, "Content is incomplete for '$algorithm'")
            }
        }
    }

    private fun getAlgorithms(): List<String> = listOf("gzip", "br", "zstd")

    private fun read(response: HttpResponse<ByteArray>, algorithm: String): String {
        // the JDK client does not decompress responses automatically
        val stream = ByteArrayInputStream(response.body())

        val decompressed: InputStream = when (algorithm) {
            "gzip" -> GZIPInputStream(stream)
            "br" -> {
                Brotli4jLoader.ensureAvailability()
                BrotliInputStream(stream)
            }
            "zstd" -> ZstdInputStream(stream)
            else -> throw IllegalArgumentException(algorithm)
        }

        return decompressed.use { it.reader().readText() }
    }

}
