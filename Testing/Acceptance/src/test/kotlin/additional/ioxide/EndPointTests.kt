package org.genhttp.testing.acceptance.additional.ioxide

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.HttpProtocols
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.content
import org.genhttp.modules.io.fromString
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.additional.netty.RawClient
import org.genhttp.testing.acceptance.utilities.AsyncFunctionalHandler

import org.junit.jupiter.api.Test

import org.slf4j.event.Level

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Not ported from GenHTTP: the endpoints of the ioxide engine bind like those of the ioxide engine of GenHTTP. Every
 * listener is bound to all interfaces, as a dual-stack socket on `[::]` or as an IPv4 socket on `0.0.0.0` (the address
 * of an endpoint is not applied, which is logged), every endpoint has the same dual-stack mode and a port of its own
 * (a connection is matched to its endpoint by the port it arrived on). A port that is in use stops the server from
 * starting, a restarted server binds its ports again, a free port is chosen for endpoints bound to port 0, and
 * endpoints that do not offer HTTP/1.1 are rejected as the engine does not support other protocols yet. The
 * addresses of IPv4 clients of dual-stack sockets are reported as IPv4 addresses.
 */
class EndPointTests {

    @Test
    fun testIPv4WithoutDualStack() = ioxideTest {
        runWith(InetAddress.getByName("0.0.0.0"), dualStack = false).use { runner ->
            assertTrue(canConnect(IPv4Loopback, runner.port))
            assertFalse(canConnect(IPv6Loopback, runner.port))
        }
    }

    @Test
    fun testDualStack() = ioxideTest {
        runWith(null, dualStack = true).use { runner ->
            assertTrue(canConnect(IPv4Loopback, runner.port))
            assertTrue(canConnect(IPv6Loopback, runner.port))
        }
    }

    @Test
    fun testAddressesAreNotApplied() = ioxideTest {
        runWith(IPv4Loopback, dualStack = true).use { runner ->
            assertTrue(canConnect(IPv4Loopback, runner.port))
            assertTrue(canConnect(IPv6Loopback, runner.port))

            val warning = "The ioxide engine binds every endpoint to all interfaces, the address $IPv4Loopback of the endpoint on port ${runner.port} is not applied"

            assertTrue(runner.logging.entries.any { it.level == Level.WARN && it.message == warning }, "Warning not logged: ${runner.logging.messages}")
        }
    }

    @Test
    fun testPortInUse() = ioxideTest {
        val runner = IoxideRunner(Content.from(Resource.fromString("Hello World!")))

        ServerSocket(runner.port).use {
            val e = assertFailsWith<RuntimeException> { runner.start() }

            assertEquals("Failed to bind to * on port ${runner.port}.", e.message)
        }

        // the failed start released everything, so the port can be used now
        runner.use {
            runner.start()

            assertTrue(canConnect(IPv4Loopback, runner.port))
        }
    }

    @Test
    fun testRestart() = ioxideTest {
        IoxideRunner.run(Content.from(Resource.fromString("Hello World!"))).use { runner ->
            RawClient.connect(runner.port).use { before ->
                // closed by the server, which leaves the connection in TIME_WAIT on the port of the server
                RawClient.send(before, RawClient.request(headers = listOf("Connection" to "close")))

                val input = before.getInputStream()

                assertEquals("Hello World!", RawClient.readResponse(input).text)
                assertEquals(-1, input.read())
            }

            runner.host.restart()

            RawClient.connect(runner.port).use { after ->
                RawClient.send(after, RawClient.request())
                assertEquals("Hello World!", RawClient.readResponse(after.getInputStream()).text)
            }
        }
    }

    @Test
    fun testFreePortIsChosen() = ioxideTest {
        IoxideRunner(Content.from(Resource.fromString("Hello World!"))).use { runner ->
            runner.host.bind(null, 0)

            runner.start()

            val port = assertNotNull(runner.host.instance).endPoints.single().port

            assertNotEquals(0, port)

            RawClient.connect(port).use { client ->
                RawClient.send(client, RawClient.request())
                assertEquals("Hello World!", RawClient.readResponse(client.getInputStream()).text)
            }
        }
    }

    @Test
    fun testOnePortCarriesOneEndpoint() = ioxideTest {
        IoxideRunner(Content.from(Resource.fromString("Hello World!"))).use { runner ->
            val port = TestHost.nextPort()

            runner.host.bind(IPv4Loopback, port)
            runner.host.bind(IPv6Loopback, port)

            val e = assertFailsWith<UnsupportedOperationException> { runner.start() }

            assertEquals(
                "Port $port was bound 2 times. A connection is matched to its endpoint by the port it arrived on, so each port carries one endpoint.",
                e.message
            )
        }
    }

    @Test
    fun testEveryEndpointSharesOneDualStackMode() = ioxideTest {
        IoxideRunner(Content.from(Resource.fromString("Hello World!"))).use { runner ->
            val first = TestHost.nextPort()
            val second = TestHost.nextPort()

            runner.host.bind(null, first, HttpProtocols.Http1, dualStack = true)
            runner.host.bind(null, second, HttpProtocols.Http1, dualStack = false)

            val e = assertFailsWith<UnsupportedOperationException> { runner.start() }

            assertEquals(
                "The ioxide engine binds every endpoint with one dual-stack mode, taken from the first one bound (port $first, DualStack = true). " +
                        "These ask for the other: $second.",
                e.message
            )
        }
    }

    @Test
    fun testOnlyHttp1IsSupported() = ioxideTest {
        IoxideRunner(Content.from(Resource.fromString("Hello World!"))).use { runner ->
            runner.host.bind(null, runner.port, HttpProtocols.Http2)

            val e = assertFailsWith<UnsupportedOperationException> { runner.start() }

            assertEquals("The ioxide engine does only support HTTP/1.1", e.message)
        }
    }

    @Test
    fun testAPortMustServeSomething() = ioxideTest {
        IoxideRunner(Content.from(Resource.fromString("Hello World!"))).use { runner ->
            runner.host.bind(null, runner.port, HttpProtocols.None)

            assertFailsWith<UnsupportedOperationException> { runner.start() }
        }
    }

    @Test
    fun testClientAddresses() = ioxideTest {
        val handler = AsyncFunctionalHandler { request -> request.respond().content(request.client.address?.hostAddress ?: "unknown").build() }

        IoxideRunner.run(handler).use { runner ->
            for ((address, expected) in listOf(IPv4Loopback to "127.0.0.1", IPv6Loopback to "0:0:0:0:0:0:0:1")) {
                Socket().use { client ->
                    client.connect(InetSocketAddress(address, runner.port), 5000)

                    RawClient.send(client, RawClient.request())

                    assertEquals(expected, RawClient.readResponse(client.getInputStream()).text)
                }
            }
        }
    }

    // region Helpers

    private suspend fun runWith(address: InetAddress?, dualStack: Boolean): IoxideRunner {
        val runner = IoxideRunner(Content.from(Resource.fromString("Hello World!")))

        runner.host.bind(address, runner.port, HttpProtocols.Http1, dualStack)

        return runner.start()
    }

    /**
     * Checks whether the server answers a request sent via the given address.
     */
    private fun canConnect(address: InetAddress, port: Int): Boolean {
        try {
            Socket().use { client ->
                client.connect(InetSocketAddress(address, port), 500)
                client.soTimeout = 2000

                val request = "GET / HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n"

                client.getOutputStream().write(request.toByteArray(Charsets.US_ASCII))
                client.getOutputStream().flush()

                val buffer = ByteArray(8)
                val read = client.getInputStream().readNBytes(buffer, 0, buffer.size)

                return read > 0 && String(buffer, 0, read, Charsets.US_ASCII).startsWith("HTTP/1.")
            }
        } catch (_: Exception) {
            return false
        }
    }

    private companion object {

        val IPv4Loopback: InetAddress = InetAddress.getByName("127.0.0.1")

        val IPv6Loopback: InetAddress = InetAddress.getByName("::1")

    }

    // endregion

}
