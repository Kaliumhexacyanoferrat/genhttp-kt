package org.genhttp.testing.acceptance.modules.serversentevents

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.serversentevents.EventConnection
import org.genhttp.modules.serversentevents.EventSource
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import java.time.LocalDate

import kotlin.test.assertContains

class DataTests {

    private val nl = "\n"

    // region Supporting data structures

    data class MyType(val one: String, val two: Int)

    // endregion

    // region Tests

    @MultiEngineTest
    fun testString(engine: ServerEngine) = test { testAsync(engine, { c -> c.data("my string") }, "my string") }

    @MultiEngineTest
    fun testInt(engine: ServerEngine) = test { testAsync(engine, { c -> c.data(42) }, "42") }

    // DateOnly.FromDayNumber(8445): the number of days since 0001-01-01
    @MultiEngineTest
    fun testDate(engine: ServerEngine) = test { testAsync(engine, { c -> c.data(LocalDate.of(1, 1, 1).plusDays(8445)) }, "0024-02-15") }

    @MultiEngineTest
    fun testComplex(engine: ServerEngine) = test { testAsync(engine, { c -> c.data(MyType("1", 2), "1", "2") }, "{\"one\":\"1\",\"two\":2}") }

    private suspend fun testAsync(engine: ServerEngine, generator: suspend (EventConnection) -> Unit, expected: String) {
        val source = EventSource.create()
            .generator(generator)

        TestHost.run(source, engine = engine).use { host ->
            val response = host.getResponse()

            response.assertStatus(ResponseStatus.Ok)

            assertContains(response.getContent(), "data: $expected$nl$nl")
        }
    }

    // endregion

}
