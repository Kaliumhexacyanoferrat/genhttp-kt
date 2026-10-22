package org.genhttp.testing.acceptance.modules.singlepageapplications

import org.genhttp.api.content.io.ResourceTree
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.io.fromDirectory
import org.genhttp.modules.singlepageapplications.SinglePageApplication
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.Chain
import org.genhttp.testing.acceptance.utilities.FileUtil
import org.genhttp.testing.getContent
import org.genhttp.testing.getContentHeader

import org.junit.jupiter.api.Test

import java.nio.file.Files
import java.nio.file.Path

import kotlin.test.assertEquals

class SinglePageTests {

    @MultiEngineTest
    fun testIndex(engine: ServerEngine) = test {
        val root = createRoot()

        FileUtil.writeTextAsync(Path.of(root, "index.html"), "This is the index!")

        TestHost.run(SinglePageApplication.from(ResourceTree.fromDirectory(root)), engine = engine).use { runner ->
            val index = runner.getResponse("/")

            index.assertStatus(ResponseStatus.Ok)
            assertEquals("text/html", index.getContentHeader("Content-Type"))

            val content = index.getContent()

            assertEquals("This is the index!", content)
        }
    }

    @MultiEngineTest
    fun testIndexServedWithRouting(engine: ServerEngine) = test {
        val root = createRoot()

        FileUtil.writeTextAsync(Path.of(root, "index.html"), "This is the index!")

        val spa = SinglePageApplication.from(ResourceTree.fromDirectory(root))
            .serverSideRouting()

        TestHost.run(spa, engine = engine).use { runner ->
            val index = runner.getResponse("/some-route/")

            index.assertStatus(ResponseStatus.Ok)
            assertEquals("text/html", index.getContentHeader("Content-Type"))
        }
    }

    @MultiEngineTest
    fun testNoIndex(engine: ServerEngine) = test {
        TestHost.run(SinglePageApplication.from(ResourceTree.fromDirectory(createRoot())), engine = engine).use { runner ->
            val index = runner.getResponse("/")

            index.assertStatus(ResponseStatus.NotFound)
        }
    }

    @MultiEngineTest
    fun testFile(engine: ServerEngine) = test {
        val root = createRoot()

        FileUtil.writeTextAsync(Path.of(root, "some.txt"), "This is some text file :)")

        TestHost.run(SinglePageApplication.from(ResourceTree.fromDirectory(root)), engine = engine).use { runner ->
            val index = runner.getResponse("/some.txt")

            index.assertStatus(ResponseStatus.Ok)
            assertEquals("text/plain", index.getContentHeader("Content-Type"))

            val content = index.getContent()

            assertEquals("This is some text file :)", content)
        }
    }

    @MultiEngineTest
    fun testNoFile(engine: ServerEngine) = test {
        TestHost.run(SinglePageApplication.from(ResourceTree.fromDirectory(createRoot())), engine = engine).use { runner ->
            val index = runner.getResponse("/nope.txt")

            index.assertStatus(ResponseStatus.NotFound)
        }
    }

    @Test
    fun testConcernChaining() {
        val app = SinglePageApplication.from(ResourceTree.fromDirectory(createRoot()))

        Chain.works(app)
    }

    private fun createRoot(): String = Files.createTempDirectory("genhttp").toAbsolutePath().toString()

}
