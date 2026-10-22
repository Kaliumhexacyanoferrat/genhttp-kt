package org.genhttp.testing.acceptance.modules.files

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.compression.algorithms.BrotliAlgorithm
import org.genhttp.modules.compression.algorithms.GzipAlgorithm
import org.genhttp.modules.files.Assets
import org.genhttp.modules.files.multi.FileAssetsBuilder
import org.genhttp.modules.files.multi.FileAssetsHandler
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.Chain
import org.genhttp.testing.getContent
import org.genhttp.testing.getContentHeader
import org.genhttp.testing.getHeader

import org.junit.jupiter.api.Test

import java.nio.file.Files

import kotlin.test.assertEquals
import kotlin.test.assertIs

class AssetsFilesTests {

    @MultiEngineTest
    fun testRegular(engine: ServerEngine) = test {
        run(engine, { host ->
            val response = host.getResponse("/file.txt")

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("This is root", response.getContent())
        })
    }

    @MultiEngineTest
    fun testSubFile(engine: ServerEngine) = test {
        run(engine, { host ->
            val response = host.getResponse("/SubDir/subfile.txt")

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("This is sub", response.getContent())
        })
    }

    @MultiEngineTest
    fun testSubFileCompressed(engine: ServerEngine) = test {
        fun adjustments(h: FileAssetsBuilder) {
            h.allowPrecompressed(arrayOf(BrotliAlgorithm()), '.')
        }

        run(engine, { host ->
            val request = host.getRequest("/SubDir/subfile.txt")

            request.header("Accept-Encoding", "br")

            val response = host.getResponse(request)

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("This is subcompressed", response.getContent())
            assertEquals("br", response.getContentHeader("Content-Encoding"))
        }, ::adjustments)
    }

    @MultiEngineTest
    fun testSubFileGzip(engine: ServerEngine) = test {
        fun adjustments(h: FileAssetsBuilder) {
            h.allowPrecompressed(arrayOf(BrotliAlgorithm(), GzipAlgorithm()), '.')
        }

        run(engine, { host ->
            val request = host.getRequest("/SubDir/subfile.txt")

            request.header("Accept-Encoding", "gzip")

            val response = host.getResponse(request)

            response.assertStatus(ResponseStatus.Ok)

            // Served from the ".gz" sibling, but announced with the "gzip" Content-Encoding.
            assertEquals("This is subgzip", response.getContent())
            assertEquals("gzip", response.getContentHeader("Content-Encoding"))
            assertEquals("Accept-Encoding", response.getHeader("Vary"))
        }, ::adjustments)
    }

    @MultiEngineTest
    fun testDirectory(engine: ServerEngine) = test {
        run(engine, { host ->
            val response = host.getResponse("/SubDir/")

            response.assertStatus(ResponseStatus.NotFound)
        })
    }

    @Test
    fun testChaining() {
        Chain.works(Assets.from("./"))
    }

    private suspend fun run(engine: ServerEngine, logic: suspend (TestHost) -> Unit, customizations: ((FileAssetsBuilder) -> Unit)? = null) {
        val dir = Files.createTempDirectory("genhttp")

        val rootFile = dir.resolve("file.txt")

        Files.writeString(rootFile, "This is root")

        val subDir = Files.createDirectory(dir.resolve("SubDir"))

        val subFile = subDir.resolve("subfile.txt")

        Files.writeString(subFile, "This is sub")

        val subFileCompressed = subDir.resolve("subfile.txt.br")

        Files.writeString(subFileCompressed, "This is subcompressed")

        // gzip's sibling uses the ".gz" extension (its FileExtension), not the ".gzip" encoding name.
        val subFileGzip = subDir.resolve("subfile.txt.gz")

        Files.writeString(subFileGzip, "This is subgzip")

        val assets = Assets.from(dir)

        customizations?.invoke(assets)

        assertIs<FileAssetsHandler>(assets.build())

        TestHost.run(assets, engine = engine).use { host ->
            logic(host)
        }
    }

}
