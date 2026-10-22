package org.genhttp.testing.acceptance.modules.files

import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.compression.algorithms.BrotliAlgorithm
import org.genhttp.modules.compression.algorithms.GzipAlgorithm
import org.genhttp.modules.files.Assets
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.Chain
import org.genhttp.testing.getContent
import org.genhttp.testing.getContentHeader
import org.genhttp.testing.getHeader

import org.junit.jupiter.api.Test

import java.nio.file.Files

import kotlin.test.assertEquals
import kotlin.test.assertNull

// Upstream these tests are executed against the ioxide engine only (and skipped otherwise). As there is only the
// internal engine in the Kotlin port, they run against it. The tests of the ioxide specific refresh interval of
// the file assets are not ported as the Kotlin port has no such setting.
class IoxideFilesTests {

    @Test
    fun testRegular() = test {
        run { host ->
            val response = host.getResponse("/file.txt")

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("This is root", response.getContent())
            assertEquals("text/plain", response.getContentHeader("Content-Type"))
        }
    }

    @Test
    fun testSubFile() = test {
        run { host ->
            val response = host.getResponse("/SubDir/subfile.txt")

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("This is sub", response.getContent())
        }
    }

    @Test
    fun testNotFound() = test {
        run { host ->
            val response = host.getResponse("/does-not-exist.txt")

            response.assertStatus(ResponseStatus.NotFound)
        }
    }

    @Test
    fun testDirectory() = test {
        run { host ->
            val response = host.getResponse("/SubDir/")

            response.assertStatus(ResponseStatus.NotFound)
        }
    }

    @Test
    fun testMethodNotAllowed() = test {
        run { host ->
            val request = host.getRequest("/file.txt", "POST")

            val response = host.getResponse(request)

            response.assertStatus(ResponseStatus.MethodNotAllowed)

            // "Allow" is comma-separated, so check the full set of tokens instead of the first header value only.
            val allowed = response.headers().allValues("Allow").flatMap { it.split(",") }.map { it.trim() }

            assertEquals(listOf("GET", "HEAD"), allowed.sorted())
        }
    }

    @Test
    fun testHead() = test {
        run { host ->
            val request = host.getRequest("/file.txt", "HEAD")

            val response = host.getResponse(request)

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("", response.getContent())
            assertEquals("This is root".length.toLong(), response.getContentHeader("Content-Length")?.toLong())
        }
    }

    @Test
    fun testPrecompressedBrotli() = test {
        run { host ->
            val request = host.getRequest("/SubDir/subfile.txt")

            request.header("Accept-Encoding", "br")

            val response = host.getResponse(request)

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("This is sub, compressed with brotli", response.getContent())
            assertEquals("br", response.getContentHeader("Content-Encoding"))
            assertEquals("Accept-Encoding", response.getHeader("Vary"))
        }
    }

    @Test
    fun testPrecompressedGzip() = test {
        run { host ->
            val request = host.getRequest("/SubDir/subfile.txt")

            request.header("Accept-Encoding", "gzip")

            val response = host.getResponse(request)

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("This is sub, compressed with gzip", response.getContent())
            assertEquals("gzip", response.getContentHeader("Content-Encoding"))
        }
    }

    @Test
    fun testPrecompressedPrefersBrotliOverGzip() = test {
        run { host ->
            val request = host.getRequest("/SubDir/subfile.txt")

            request.header("Accept-Encoding", "br, gzip")

            val response = host.getResponse(request)

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("br", response.getContentHeader("Content-Encoding"))
        }
    }

    @Test
    fun testNoAcceptEncodingServesIdentity() = test {
        run { host ->
            val response = host.getResponse("/SubDir/subfile.txt")

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("This is sub", response.getContent())
            assertNull(response.getContentHeader("Content-Encoding"))
        }
    }

    @Test
    fun testLargeFileIsServedFromDisk() = test {
        val dir = Files.createTempDirectory("genhttp")

        // AssetCache.DefaultMaxCachedFileBytes is 256 KB - go over it so the asset is read off the ring
        // instead of being served from the baked in-memory response.
        val content = "0123456789".repeat(30_000)

        Files.writeString(dir.resolve("large.txt"), content)

        val handler = Assets.from(dir.toString())

        TestHost.run(handler).use { host ->
            for (i in 0 until 3) {
                val response = host.getResponse("/large.txt")

                response.assertStatus(ResponseStatus.Ok)

                assertEquals(content, response.getContent())
            }
        }
    }

    @Test
    fun testChangedFileServesUpdatedContent() = test {
        val dir = Files.createTempDirectory("genhttp")

        val file = dir.resolve("file.txt")

        Files.writeString(file, "This is root")

        val handler = Assets.from(dir.toString())

        // Edited after the cache snapshot was taken, with a different length so IsFresh's size check fails.
        Files.writeString(file, "This is the updated content")

        TestHost.run(handler).use { host ->
            val response = host.getResponse("/file.txt")

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("This is the updated content", response.getContent())
        }
    }

    @Test
    fun testChaining() {
        val dir = Files.createTempDirectory("genhttp")

        Chain.works(Assets.from(dir.toString()))
    }

    private suspend fun run(logic: suspend (TestHost) -> Unit) {
        val dir = Files.createTempDirectory("genhttp")

        val rootFile = dir.resolve("file.txt")

        Files.writeString(rootFile, "This is root")

        val subDir = Files.createDirectory(dir.resolve("SubDir"))

        val subFile = subDir.resolve("subfile.txt")

        Files.writeString(subFile, "This is sub")

        val subFileBrotli = subDir.resolve("subfile.txt.br")

        Files.writeString(subFileBrotli, "This is sub, compressed with brotli")

        // The sibling extension follows the algorithm's FileExtension ("gz"), matching the regular handler.
        val subFileGzip = subDir.resolve("subfile.txt.gz")

        Files.writeString(subFileGzip, "This is sub, compressed with gzip")

        val handler = Assets.from(dir.toString())
            .allowPrecompressed(arrayOf(BrotliAlgorithm(), GzipAlgorithm()), '.')

        TestHost.run(handler).use { host ->
            logic(host)
        }
    }

}
