package org.genhttp.testing.acceptance.modules.archives

import org.genhttp.api.content.io.Resource
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.PathSegment
import org.genhttp.api.protocol.RequestTarget
import org.genhttp.engine.shared.types.HttpRequestTarget
import org.genhttp.modules.archives.ArchiveTree
import org.genhttp.modules.io.find
import org.genhttp.modules.io.fromClasspath
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.NonSeekableResource

import org.junit.jupiter.api.Test

import kotlin.test.assertEquals
import kotlin.test.assertNotNull

open class FunctionalTests {

    // region Tests

    @Test
    fun testFormats() = test {
        for (format in arrayOf("zip", "tar.gz", "7z")) {
            val file = Resource.fromClasspath("Resources/Archives/Archive.$format")

            val tree = ArchiveTree.from(file).build()

            assertNotNull(tree.modified)

            val target = getTarget("/SubDir/SubDir/SubFile.txt")

            val (foundNode, foundFile) = tree.find(target)

            assertNotNull(foundNode)
            assertNotNull(foundFile)

            assertEquals("SubFile.txt", foundFile.name)

            assertEquals(ContentType.TextPlain, foundFile.contentType)
            assertNotNull(foundFile.modified)

            assertEquals("3", getContent(foundFile))
        }
    }

    @Test
    fun testNonSeekableResource() = test {
        val source = NonSeekableResource(Resource.fromClasspath("Resources/Archives/Archive.zip").build())

        val tree = ArchiveTree.from(source).build()

        val target = getTarget("/SubDir/SubDir/SubFile.txt")

        val (_, foundFile) = tree.find(target)

        assertEquals("3", getContent(foundFile!!))
    }

    @Test
    fun testNavigation() = test {
        val source = Resource.fromClasspath("Resources/Archives/Archive.zip")

        val tree = ArchiveTree.from(source).build()

        assertEquals(1, tree.getNodes().size)
        assertEquals(1, tree.getResources().size)

        assertNotNull(tree.tryGetResource(PathSegment("RootFile.txt")))

        assertNotNull(tree.tryGetNode(PathSegment("SubDir")))

        assertNotNull(tree.modified)
    }

    @Test
    fun testResourceImplementation() = test {
        val source = Resource.fromClasspath("Resources/Archives/Archive.tar.gz").build()

        val tree = ArchiveTree.from(source).build()

        val file = tree.tryGetResource(PathSegment("RootFile.txt"))!!

        file.calculateChecksum()

        assertEquals(1L, file.length)
    }

    // endregion

    // region Helpers

    private suspend fun getContent(resource: Resource): String =
        resource.getContent().use { content -> content.reader().readText() }

    private fun getTarget(path: String): RequestTarget {
        val target = HttpRequestTarget()
        target.apply(ByteString(path))

        return target
    }

    // endregion

}
