package org.genhttp.testing.acceptance.modules.staticwebsites

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.io.VirtualTree
import org.genhttp.modules.io.fromString
import org.genhttp.modules.staticwebsites.StaticWebsite
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.Chain
import org.genhttp.testing.getContent

import org.junit.jupiter.api.Test

import kotlin.test.assertEquals

class StaticWebsiteTests {

    @MultiEngineTest
    fun testWithIndex(engine: ServerEngine) = test {
        val tree = VirtualTree.create()
            .add("index.html", Resource.fromString("Index 1"))
            .add("sub", VirtualTree.create().add("index.htm", Resource.fromString("Index 2")))

        TestHost.run(StaticWebsite.from(tree), engine = engine).use { runner ->
            val indexResponse = runner.getResponse()
            assertEquals("Index 1", indexResponse.getContent())

            val subIndexResponse = runner.getResponse("/sub/")
            assertEquals("Index 2", subIndexResponse.getContent())
        }
    }

    @MultiEngineTest
    fun testNoIndex(engine: ServerEngine) = test {
        val tree = VirtualTree.create()
            .add("sub", VirtualTree.create())

        TestHost.run(StaticWebsite.from(tree), engine = engine).use { runner ->
            val indexResponse = runner.getResponse()
            indexResponse.assertStatus(ResponseStatus.NotFound)

            val subIndexResponse = runner.getResponse("/sub/")
            subIndexResponse.assertStatus(ResponseStatus.NotFound)
        }
    }

    @Test
    fun testConcernChaining() {
        val site = StaticWebsite.from(VirtualTree.create())

        Chain.works(site)
    }

}
