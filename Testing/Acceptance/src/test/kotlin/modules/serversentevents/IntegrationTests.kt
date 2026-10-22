package org.genhttp.testing.acceptance.modules.serversentevents

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.conversion.Formatting
import org.genhttp.modules.serversentevents.EventSource
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.Chain
import org.genhttp.testing.getContent

import java.time.LocalDate

import kotlin.test.Test
import kotlin.test.assertEquals

class IntegrationTests {

    @MultiEngineTest
    fun testCustomFormatting(engine: ServerEngine) = test {
        val source = EventSource.create()
            .formatting(Formatting.empty())
            .generator { c -> c.data(LocalDate.of(9999, 12, 31)) }

        TestHost.run(source, engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("data: \"9999-12-31\"\n\n", response.getContent())
        }
    }

    @Test
    fun testConcernChaining() {
        Chain.works(EventSource.create().generator { })
    }

}
