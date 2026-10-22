package org.genhttp.testing.acceptance.modules.functional

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.conversion.Formatting
import org.genhttp.modules.conversion.formatters.BoolFormatter
import org.genhttp.modules.functional.*
import org.genhttp.modules.reflection.ExecutionMode
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineFrameworkTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import kotlin.test.assertEquals

class IntegrationTest {

    @MultiEngineFrameworkTest
    fun testFormatters(engine: ServerEngine, mode: ExecutionMode) = test {
        val formatting = Formatting.empty()
            .add<BoolFormatter>()

        val api = Inline.create()
            .any("get-bool") { value: Boolean -> value }
            .formatters(formatting)
            .executionMode(mode)

        TestHost.run(api, engine = engine).use { host ->
            val response = host.getResponse("/get-bool?value=1")

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("1", response.getContent())
        }
    }

}
