package org.genhttp.testing.acceptance.engine

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.modules.layouting.Layout
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test

import org.junit.jupiter.api.Test

import kotlin.test.assertEquals

class WireTests : WireTest() {

    // region Tests

    @Test
    fun testNoHost() = test {
        assertWire("GET / HTTP/1.0\r\n", "Host")
    }

    @Test
    fun testUnsupportedProtocolVersion() = test {
        assertWire("GET / HTTP/2.0\r\n", "Invalid HTTP version")
    }

    @Test
    fun testUnexpectedProtocol() = test {
        assertWire("GET / GENHTTP/1.0\r\n", "Invalid HTTP version")
    }

    @Test
    fun testNoKeepAliveForHttp10() = test {
        TestHost.run(Layout.create()).use { host ->
            val result = send(host) { w ->
                w.write("GET / HTTP/1.0$NL")
                w.write("Host: 127.0.0.1$NL")
                w.write(NL)
            }

            AssertX.doesNotContain("Keep-Alive", result)
        }
    }

    /**
     * A client spells the Connection header in whatever case it likes - browsers and curl both
     * send "keep-alive" - so the value has to be compared case-insensitively. Two requests are
     * pipelined onto one connection, the first asking to keep it alive in lowercase and the second
     * to close it: honouring the first is exactly what lets the second be answered at all, so a
     * case-sensitive comparison closes after the first and only one response comes back.
     */
    @MultiEngineTest
    fun testKeepAliveIsCaseInsensitive(engine: ServerEngine) = test {
        TestHost.run(Layout.create(), engine = engine).use { host ->
            val result = send(host) { w ->
                w.write("GET / HTTP/1.1$NL")
                w.write("Host: 127.0.0.1$NL")
                w.write("Connection: keep-alive$NL")
                w.write(NL)

                w.write("GET / HTTP/1.1$NL")
                w.write("Host: 127.0.0.1$NL")
                w.write("Connection: close$NL")
                w.write(NL)
            }

            val responses = result.split("HTTP/1.1 ").size - 1

            assertEquals(2, responses, "The lowercase keep-alive was not honoured, so the connection closed before the second request could be answered.")
        }
    }

    @Test
    fun testNoKeepAliveForConnectionClose() = test {
        TestHost.run(Layout.create()).use { host ->
            val result = send(host) { w ->
                w.write("GET / HTTP/1.1$NL")
                w.write("Host: 127.0.0.1$NL")
                w.write("Connection: close$NL")
                w.write(NL)
            }

            AssertX.doesNotContain("Keep-Alive", result)
        }
    }

    // endregion

}
