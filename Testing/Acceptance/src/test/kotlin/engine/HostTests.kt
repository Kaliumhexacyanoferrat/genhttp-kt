package org.genhttp.testing.acceptance.engine

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.engine.internal.Host
import org.genhttp.modules.layouting.Layout
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test

import org.junit.jupiter.api.Test

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class HostTests {

    @Test
    fun testPortZeroThrows() {
        val host = Host.create()

        assertFailsWith<IllegalArgumentException> { host.port(0) }
    }

    @Test
    fun testRunAsyncReturnsErrorCodeWithoutHandler() = test {
        val host = Host.create()

        val exitCode = host.run()

        assertEquals(-1, exitCode)
    }

    @MultiEngineTest
    fun testStart(engine: ServerEngine) = test {
        TestHost(Layout.create().build(), engine = engine).use { runner ->
            runner.host.start()

            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.NotFound)
        }
    }

    @MultiEngineTest
    fun testRestart(engine: ServerEngine) = test {
        TestHost(Layout.create().build(), engine = engine).use { runner ->
            runner.host.restart()

            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.NotFound)
        }
    }

}
