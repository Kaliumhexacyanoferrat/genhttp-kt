package org.genhttp.testing.acceptance.modules.io

import org.genhttp.api.content.io.Resource
import org.genhttp.api.content.io.ResourceBuilder
import org.genhttp.api.protocol.ContentType
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.fromClasspath
import org.genhttp.modules.io.fromFile
import org.genhttp.modules.io.fromString
import org.genhttp.modules.layouting.Layout
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.FileUtil
import org.genhttp.testing.getContent

import org.junit.jupiter.api.Test

import java.io.FileNotFoundException
import java.nio.file.Files
import java.time.Instant

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ResourceTest {

    @Test
    fun testStringResource() = test {
        val resource = Resource.fromString("Hello World")
            .build()

        assertEquals(ContentType.TextPlain, resource.contentType)

        resource.getContent().use { content ->
            assertEquals(11, content.readAllBytes().size)
        }

        assertEquals(11L, resource.length!!)

        assertNull(resource.modified)
        assertNull(resource.name)
    }

    @Test
    fun testFileResource() = test {
        val file = Files.createTempFile("genhttp", ".tmp")

        try {
            FileUtil.writeTextAsync(file, "Hello World")

            val resource = Resource.fromFile(file)
                .build()

            resource.getContent().use { content ->
                assertEquals(11, content.readAllBytes().size)
            }

            assertEquals(11L, resource.length!!)

            assertNotNull(resource.modified)
            assertNotNull(resource.name)
        } finally {
            try {
                Files.deleteIfExists(file)
            } catch (_: Exception) { /* nop */
            }
        }
    }

    @Test
    fun testAssemblyResource() = test {
        val resource = Resource.fromClasspath("Resources/File.txt")
            .build()

        assertEquals(ContentType.TextPlain, resource.contentType)

        resource.getContent().use { content ->
            assertEquals(16, content.readAllBytes().size)
        }

        assertEquals(16L, resource.length!!)

        assertNotNull(resource.modified)
    }

    @Test
    fun testAssemblyResourceRouting() = test {
        val layout = Layout.create()
            .add("1", Content.from(Resource.fromClasspath("Resources/File.txt")))
            .add("2", Content.from(Resource.fromClasspath("Resources/OtherFile.txt")))

        TestHost.run(layout).use { runner ->
            val f1 = runner.getResponse("/1")
            assertEquals("This is text!", f1.getContent())

            val f2 = runner.getResponse("/2")
            assertEquals("This is other text!", f2.getContent())
        }
    }

    @Test
    fun testStringMetaData() {
        testMetaData(Resource.fromString("Hello World"))
    }

    @Test
    fun testEmbeddedMetaData() {
        testMetaData(Resource.fromClasspath("Resources/File.txt"))
    }

    @Test
    fun testFileMetaData() {
        val file = Files.createTempFile("genhttp", ".tmp")

        FileUtil.writeText(file, "blubb")

        try {
            testMetaData(Resource.fromFile(file), false)
        } finally {
            try {
                Files.deleteIfExists(file)
            } catch (_: Exception) { /* nop */
            }
        }
    }

    private fun <T : ResourceBuilder<T>> testMetaData(builder: ResourceBuilder<T>, modified: Boolean = true) {
        val now = Instant.now()

        builder.name("MyFile.txt")
            .type(ContentType.VideoH264)

        if (modified) {
            builder.modified(now)
        }

        val resource = builder.build()

        assertEquals("MyFile.txt", resource.name)
        assertEquals(ContentType.VideoH264, resource.contentType)

        if (modified) {
            assertEquals(now, resource.modified)
        }
    }

    @Test
    fun testNonExistentFile() {
        assertFailsWith<FileNotFoundException> { Resource.fromFile("blubb.txt").build() }
    }

}
