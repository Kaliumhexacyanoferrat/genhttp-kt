package org.genhttp.testing.acceptance.modules.archives

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.archives.ArchiveTree
import org.genhttp.modules.io.fromClasspath
import org.genhttp.modules.staticwebsites.StaticWebsite
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import kotlin.test.assertEquals

open class IntegrationTests {

    @MultiEngineTest
    fun testStaticFiles(engine: ServerEngine) = test {
        val tree = ArchiveTree.from(Resource.fromClasspath("Resources/Archives/Archive.zip"))

        val app = StaticWebsite.from(tree)

        TestHost.run(app, engine = engine).use { runner ->
            val response = runner.getResponse("/SubDir/SubFile.txt")

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("2", response.getContent())
        }
    }

}
