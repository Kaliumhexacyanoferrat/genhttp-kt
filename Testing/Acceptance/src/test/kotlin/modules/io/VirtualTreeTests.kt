package org.genhttp.testing.acceptance.modules.io

import org.genhttp.api.content.io.ResourceNode
import org.genhttp.api.content.io.Resource
import org.genhttp.api.content.io.ResourceTree
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.RequestTarget
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.engine.shared.types.HttpRequestTarget
import org.genhttp.modules.directorybrowsing.Listing
import org.genhttp.modules.io.VirtualTree
import org.genhttp.modules.io.find
import org.genhttp.modules.io.fromClasspath
import org.genhttp.modules.io.fromString
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test

import org.junit.jupiter.api.Test

import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class VirtualTreeTests {

    @Test
    fun testNestedTree() = test {
        val tree = ResourceTree.fromClasspath("Resources")

        val virt = VirtualTree.create()
            .add("r", tree)
            .build()

        val (node, file) = virt.find(getTarget("/r/File.txt"))

        assertNotNull(node)
        assertNotNull(file)

        assertEquals(virt, (node as? ResourceNode)?.parent)

        assertNotNull(virt.modified)
    }

    @Test
    fun testResource() = test {
        val virt = VirtualTree.create()
            .add("res.txt", Resource.fromString("Blubb"))
            .build()

        val (node, file) = virt.find(getTarget("/res.txt"))

        assertNotNull(node)
        assertNotNull(file)
    }

    @MultiEngineTest
    fun testUsage(engine: ServerEngine) = test {
        val tree = ResourceTree.fromClasspath("Resources")

        val virt = VirtualTree.create()
            .add("r", tree)
            .add("res.txt", Resource.fromString("Blubb"))

        val handler = Listing.from(virt)

        TestHost.run(handler, engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.Ok)
        }
    }

    private fun getTarget(path: String): RequestTarget {
        val target = HttpRequestTarget()
        target.apply(ByteString(path))

        return target
    }

}
