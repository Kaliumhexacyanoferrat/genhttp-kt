package org.genhttp.testing.acceptance.modules.inspection

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.inspection.addInspector
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.fromString
import org.genhttp.modules.layouting.Layout
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import kotlin.test.assertEquals

class InspectionTests {

    @MultiEngineTest
    fun testInspection(engine: ServerEngine) = test {
        val app = Content.from(Resource.fromString("Hello World")).addInspector()

        TestHost.run(app, engine = engine).use { host ->
            val inspected = host.getResponse("/one/two?inspect")

            inspected.assertStatus(ResponseStatus.Ok)

            val content = inspected.getContent()

            AssertX.contains("/one/two", content)
        }
    }

    @MultiEngineTest
    fun testNoInspection(engine: ServerEngine) = test {
        val app = Content.from(Resource.fromString("Hello World")).addInspector()

        TestHost.run(app, engine = engine).use { host ->
            val notInspected = host.getResponse("/one/two")

            notInspected.assertStatus(ResponseStatus.Ok)

            assertEquals("Hello World", notInspected.getContent())
        }
    }

    @MultiEngineTest
    fun testNotFoundInspected(engine: ServerEngine) = test {
        val app = Layout.create().addInspector()

        TestHost.run(app, engine = engine).use { host ->
            val inspected = host.getResponse("/one/two?inspect")

            inspected.assertStatus(ResponseStatus.Ok)

            AssertX.contains("/one/two", inspected.getContent())
        }
    }

}
