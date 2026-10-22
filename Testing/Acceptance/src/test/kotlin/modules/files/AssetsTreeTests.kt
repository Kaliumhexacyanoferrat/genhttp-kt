package org.genhttp.testing.acceptance.modules.files

import org.genhttp.api.content.io.ResourceTree
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.files.Assets
import org.genhttp.modules.io.fromClasspath
import org.genhttp.modules.io.fromDirectory
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.Chain
import org.genhttp.testing.getContent

import org.junit.jupiter.api.Test

import kotlin.test.assertEquals

class AssetsTreeTests {

    @MultiEngineTest
    fun testFileDownload(engine: ServerEngine) = test {
        TestHost.run(Assets.from(ResourceTree.fromClasspath("")), engine = engine).use { runner ->
            val response = runner.getResponse("/Resources/File.txt")

            response.assertStatus(ResponseStatus.Ok)
            assertEquals("This is text!", response.getContent())
        }
    }

    @MultiEngineTest
    fun testSubdirectoryFileDownload(engine: ServerEngine) = test {
        TestHost.run(Assets.from(ResourceTree.fromClasspath("")), engine = engine).use { runner ->
            val response = runner.getResponse("/Resources/Subdirectory/AnotherFile.txt")

            response.assertStatus(ResponseStatus.Ok)
            assertEquals("This is another text!", response.getContent())
        }
    }

    @MultiEngineTest
    fun testNoFileDownload(engine: ServerEngine) = test {
        TestHost.run(Assets.from(ResourceTree.fromClasspath("")), engine = engine).use { runner ->
            val response = runner.getResponse("/Resources/nah.txt")

            response.assertStatus(ResponseStatus.NotFound)
        }
    }

    @MultiEngineTest
    fun testNoSubdirectoryFileDownload(engine: ServerEngine) = test {
        TestHost.run(Assets.from(ResourceTree.fromClasspath("")), engine = engine).use { runner ->
            val response = runner.getResponse("/Resources/nah/File.txt")

            response.assertStatus(ResponseStatus.NotFound)
        }
    }

    @MultiEngineTest
    fun testRootDownload(engine: ServerEngine) = test {
        TestHost.run(Assets.from(ResourceTree.fromClasspath("Resources")), engine = engine).use { runner ->
            val response = runner.getResponse("/File.txt")

            response.assertStatus(ResponseStatus.Ok)
            assertEquals("This is text!", response.getContent())
        }
    }

    @MultiEngineTest
    fun testDirectory(engine: ServerEngine) = test {
        TestHost.run(Assets.from(ResourceTree.fromClasspath("")), engine = engine).use { runner ->
            val response = runner.getResponse("/Resources/nah/")

            response.assertStatus(ResponseStatus.NotFound)
        }
    }

    @MultiEngineTest
    fun testNonExistingDirectory(engine: ServerEngine) = test {
        TestHost.run(Assets.from(ResourceTree.fromClasspath("")), engine = engine).use { runner ->
            val response = runner.getResponse("/Resources/nah/")

            response.assertStatus(ResponseStatus.NotFound)
        }
    }

    @Test
    fun testChaining() {
        Chain.works(Assets.from(ResourceTree.fromDirectory("./")))
    }

}
