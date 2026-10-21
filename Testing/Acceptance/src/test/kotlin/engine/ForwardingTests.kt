package org.genhttp.testing.acceptance.engine

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ClientProtocol
import org.genhttp.api.protocol.Forwarding
import org.genhttp.api.protocol.getForwardings
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.FunctionalHandler
import org.genhttp.testing.acceptance.wrap

import java.net.InetAddress
import java.net.http.HttpRequest

import kotlin.test.assertEquals
import kotlin.test.assertNull

class ForwardingTests {

    @MultiEngineTest
    fun testForwardedHeaderIsRead(engine: ServerEngine) = test {
        val forwardings = loadForwardings(engine) { request ->
            request.header("Forwarded", "for=85.192.1.5; host=google.com; proto=https")
        }

        assertEquals(1, forwardings.size)

        assertEquals(InetAddress.ofLiteral("85.192.1.5"), forwardings[0].forAddress)
        assertEquals("google.com", forwardings[0].host)
        assertEquals(ClientProtocol.Https, forwardings[0].protocol)
        assertNull(forwardings[0].by)
    }

    @MultiEngineTest
    fun testForwardedHeaderSupportsMultipleHops(engine: ServerEngine) = test {
        val forwardings = loadForwardings(engine) { request ->
            request.header("Forwarded", "for=85.192.1.1;by=10.0.0.1, for=85.192.1.2;host=google.com;proto=http")
        }

        assertEquals(2, forwardings.size)

        assertEquals(InetAddress.ofLiteral("85.192.1.1"), forwardings[0].forAddress)
        assertEquals(InetAddress.ofLiteral("10.0.0.1"), forwardings[0].by)

        assertEquals(InetAddress.ofLiteral("85.192.1.2"), forwardings[1].forAddress)
        assertEquals("google.com", forwardings[1].host)
        assertEquals(ClientProtocol.Http, forwardings[1].protocol)
    }

    @MultiEngineTest
    fun testForwardedHeaderSupportsQuotedIPv6(engine: ServerEngine) = test {
        val forwardings = loadForwardings(engine) { request ->
            request.header("Forwarded", "for=\"[2001:db8:cafe::17]:4711\"")
        }

        assertEquals(1, forwardings.size)

        assertEquals(InetAddress.ofLiteral("2001:db8:cafe::17"), forwardings[0].forAddress)
    }

    @MultiEngineTest
    fun testLegacyHeadersAreRead(engine: ServerEngine) = test {
        val forwardings = loadForwardings(engine) { request ->
            request.header("X-Forwarded-For", "85.192.1.5")
            request.header("X-Forwarded-Host", "google.com")
            request.header("X-Forwarded-Proto", "http")
        }

        assertEquals(1, forwardings.size)

        assertEquals(InetAddress.ofLiteral("85.192.1.5"), forwardings[0].forAddress)
        assertEquals("google.com", forwardings[0].host)
        assertEquals(ClientProtocol.Http, forwardings[0].protocol)
    }

    @MultiEngineTest
    fun testLegacyHeadersSupportMultipleHops(engine: ServerEngine) = test {
        val forwardings = loadForwardings(engine) { request ->
            request.header("X-Forwarded-For", "85.192.1.5, 10.0.0.1")
        }

        assertEquals(1, forwardings.size)

        assertEquals(InetAddress.ofLiteral("85.192.1.5"), forwardings[0].forAddress)
    }

    @MultiEngineTest
    fun testForwardedHeaderTakesPrecedenceOverLegacy(engine: ServerEngine) = test {
        val forwardings = loadForwardings(engine) { request ->
            request.header("Forwarded", "for=85.192.1.1; host=google.com; proto=https")

            request.header("X-Forwarded-For", "85.192.1.2")
            request.header("X-Forwarded-Host", "google2.com")
            request.header("X-Forwarded-Proto", "http")
        }

        assertEquals(1, forwardings.size)

        assertEquals(InetAddress.ofLiteral("85.192.1.1"), forwardings[0].forAddress)
        assertEquals("google.com", forwardings[0].host)
        assertEquals(ClientProtocol.Https, forwardings[0].protocol)
    }

    @MultiEngineTest
    fun testInvalidValuesAreIgnored(engine: ServerEngine) = test {
        val forwardings = loadForwardings(engine) { request ->
            request.header("Forwarded", "for=not-an-ip; host=google.com; proto=not-a-protocol")
        }

        assertEquals(1, forwardings.size)

        assertNull(forwardings[0].forAddress)
        assertEquals("google.com", forwardings[0].host)
        assertNull(forwardings[0].protocol)
    }

    @MultiEngineTest
    fun testNoHeadersResultInEmptyList(engine: ServerEngine) = test {
        val forwardings = loadForwardings(engine) { }

        assertEquals(0, forwardings.size)
    }

    private suspend fun loadForwardings(engine: ServerEngine, configure: (HttpRequest.Builder) -> Unit): List<Forwarding> {
        var forwardings: List<Forwarding>? = null

        val handler = FunctionalHandler { r ->
            forwardings = r.header.headers.getForwardings()

            r.respond().build()
        }

        TestHost.run(handler.wrap(), engine = engine).use { runner ->
            val request = runner.getRequest()
            configure(request)

            runner.getResponse(request)
        }

        return forwardings ?: emptyList()
    }

}
