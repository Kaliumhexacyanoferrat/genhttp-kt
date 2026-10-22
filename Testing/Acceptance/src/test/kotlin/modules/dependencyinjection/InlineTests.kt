package org.genhttp.testing.acceptance.modules.dependencyinjection

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.modules.dependencyinjection.DependentInline
import org.genhttp.modules.functional.get
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import kotlin.test.assertEquals

class InlineTests {

    @MultiEngineTest
    fun testParameterInjection(engine: ServerEngine) = test {
        val app = DependentInline.create()
            .get { s: AwesomeService -> s.doWork() }

        DependentHost.run(app, engine = engine).use { runner ->
            val response = runner.getResponse()

            assertEquals("42", response.getContent())
        }
    }

}
