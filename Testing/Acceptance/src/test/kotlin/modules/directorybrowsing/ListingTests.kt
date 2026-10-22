package org.genhttp.testing.acceptance.modules.directorybrowsing

import org.genhttp.api.content.io.ResourceTree
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.directorybrowsing.Listing
import org.genhttp.modules.io.fromDirectory
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.getETag
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.Chain
import org.genhttp.testing.acceptance.utilities.FileUtil
import org.genhttp.testing.getContent

import org.junit.jupiter.api.Test

import java.nio.file.Files

import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ListingTests {

    /**
     * As a user of a web application, I can view the folders and files available
     * on root level of a listed directory.
     */
    @MultiEngineTest
    fun testGetMainListing(engine: ServerEngine) = test {
        getEnvironment(engine).use { runner ->
            val response = runner.getResponse("/")

            val content = response.getContent()

            AssertX.contains("Subdirectory", content)
            AssertX.contains("With%20Spaces", content)

            AssertX.contains("my.txt", content)

            AssertX.doesNotContain("..", content)
        }
    }

    /**
     * As an user of a web application, I can view the folders and files available
     * within a subdirectory of a listed directory.
     */
    @MultiEngineTest
    fun testGetSubdirectory(engine: ServerEngine) = test {
        getEnvironment(engine).use { runner ->
            val response = runner.getResponse("/Subdirectory/")

            val content = response.getContent()

            AssertX.contains("..", content)
        }
    }

    /**
     * As an user of a web application, I can download the files listed by the
     * directory listing feature.
     */
    @MultiEngineTest
    fun testDownload(engine: ServerEngine) = test {
        getEnvironment(engine).use { runner ->
            val response = runner.getResponse("/my.txt")

            assertEquals("Hello World!", response.getContent())
        }
    }

    @MultiEngineTest
    fun testNonExistingFolder(engine: ServerEngine) = test {
        getEnvironment(engine).use { runner ->
            val response = runner.getResponse("/idonotexist/")

            response.assertStatus(ResponseStatus.NotFound)
        }
    }

    @MultiEngineTest
    fun testSameListingSameChecksum(engine: ServerEngine) = test {
        getEnvironment(engine).use { runner ->
            val resp1 = runner.getResponse()
            val resp2 = runner.getResponse()

            assertNotNull(resp1.getETag())

            assertEquals(resp1.getETag(), resp2.getETag())
        }
    }

    @Test
    fun testConcernChaining() {
        val listing = Listing.from(ResourceTree.fromDirectory("./"))

        Chain.works(listing)
    }

    private suspend fun getEnvironment(engine: ServerEngine): TestHost {
        val tempFolder = Files.createTempDirectory("genhttp")

        Files.createDirectories(tempFolder)

        Files.createDirectories(tempFolder.resolve("Subdirectory"))

        Files.createDirectories(tempFolder.resolve("With Spaces"))

        FileUtil.writeText(tempFolder.resolve("my.txt"), "Hello World!")

        val listing = Listing.from(ResourceTree.fromDirectory(tempFolder))

        return TestHost.run(listing, engine = engine)
    }

}
