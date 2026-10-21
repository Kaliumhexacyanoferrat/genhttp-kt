package org.genhttp.testing.acceptance.modules.io

import org.genhttp.api.content.io.ResourceTree
import org.genhttp.api.protocol.PathSegment
import org.genhttp.modules.io.fromClasspath
import org.genhttp.testing.acceptance.test

import org.junit.jupiter.api.Test

import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ResourceTreeTests {

    @Test
    fun testAssemblyByName() = test {
        val tree = ResourceTree.fromClasspath("Resources").build()

        assertNotNull(tree.tryGetNode(PathSegment("Subdirectory")))

        assertNotNull(tree.tryGetResource(PathSegment("File.txt")))

        assertEquals(2, tree.getNodes().size)

        assertEquals(7, tree.getResources().size)
    }

    @Test
    fun testByAssembly() = test {
        // the root of the classpath takes the place of the executing assembly; unlike an assembly, the classpath
        // root merges all roots of the test runtime (classes, resources, ...), so instead of exactly one node
        // (the "Resources" folder of the assembly) the embedded "Resources" folder must be found (see PORTING.md)
        val tree = ResourceTree.fromClasspath("").build()

        assertEquals(1, tree.getNodes().count { it.name == "Resources" })
    }

}
