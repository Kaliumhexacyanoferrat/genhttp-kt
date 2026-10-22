package org.genhttp.testing.acceptance.modules.reflection

import org.genhttp.api.content.io.ResourceTree
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.files.Assets
import org.genhttp.modules.functional.*
import org.genhttp.modules.reflection.ExecutionMode
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineFrameworkTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WildcardTests {

    @MultiEngineFrameworkTest
    fun testRouting(engine: ServerEngine, mode: ExecutionMode) = test {
        val tree = ResourceTree.fromClasspath("Resources")

        val resources = Assets.from(tree)

        val app = Inline.create()
            .get("/tenant/:tenantId/") { tenantId: Int ->
                assertTrue(tenantId > 0)
                resources
            }
            .executionMode(mode)

        TestHost.run(app, engine = engine).use { host ->
            val root = host.getResponse("/tenant/1/File.txt")

            root.assertStatus(ResponseStatus.Ok)
            assertEquals("This is text!", root.getContent())

            val sub = host.getResponse("/tenant/42/Subdirectory/AnotherFile.txt")

            sub.assertStatus(ResponseStatus.Ok)
            assertEquals("This is another text!", sub.getContent())
        }
    }

}
