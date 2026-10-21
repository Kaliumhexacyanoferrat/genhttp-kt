package org.genhttp.testing.acceptance.engine

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.FunctionalHandler
import org.genhttp.testing.acceptance.wrap
import org.genhttp.testing.getHeader

import kotlin.test.assertEquals

class HeaderTests {

    @MultiEngineTest
    fun testServerHeaderCanBeSet(engine: ServerEngine) = test {
        val handler = FunctionalHandler { r ->
            r.respond()
                .header("Server", "TFB")
                .build()
        }

        TestHost.run(handler.wrap(), engine = engine).use { runner ->
            val response = runner.getResponse()

            assertEquals("TFB", response.getHeader("Server"))
        }
    }

    @MultiEngineTest
    fun testDateHeaderCanBeSet(engine: ServerEngine) = test {
        val handler = FunctionalHandler { r ->
            r.respond()
                .header("Date", "123")
                .build()
        }

        TestHost.run(handler.wrap(), engine = engine).use { runner ->
            val response = runner.getResponse()

            assertEquals("123", response.getHeader("Date"))
        }
    }

}
