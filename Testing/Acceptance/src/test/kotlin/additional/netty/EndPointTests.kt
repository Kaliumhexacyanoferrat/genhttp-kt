package org.genhttp.testing.acceptance.additional.netty

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.HttpProtocols
import org.genhttp.engine.netty.EngineOptions
import org.genhttp.engine.netty.EventLoopOptions
import org.genhttp.engine.netty.Transport
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.fromString
import org.genhttp.testing.acceptance.test

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Not ported from GenHTTP: the endpoints of the Netty engine bind the same way on every transport (the multi-engine
 * tests run with the automatic choice only). Endpoints without dual stack only serve clients of their protocol
 * family, a port that is in use stops the server from starting, a restarted server binds its ports again, and
 * endpoints that do not offer HTTP/1.1 are rejected as the engine does not support other protocols yet.
 */
class EndPointTests {

    @ParameterizedTest
    @MethodSource("transports")
    fun testIPv4WithoutDualStack(transport: Transport) = test {
        runWith(transport, InetAddress.getByName("0.0.0.0"), dualStack = false).use { runner ->
            assertTrue(canConnect(IPv4Loopback, runner.port))
            assertFalse(canConnect(IPv6Loopback, runner.port))
        }
    }

    @ParameterizedTest
    @MethodSource("transports")
    fun testIPv6WithoutDualStack(transport: Transport) = test {
        runWith(transport, InetAddress.getByName("::"), dualStack = false).use { runner ->
            assertFalse(canConnect(IPv4Loopback, runner.port))
            assertTrue(canConnect(IPv6Loopback, runner.port))
        }
    }

    @ParameterizedTest
    @MethodSource("transports")
    fun testDualStack(transport: Transport) = test {
        runWith(transport, null, dualStack = true).use { runner ->
            assertTrue(canConnect(IPv4Loopback, runner.port))
            assertTrue(canConnect(IPv6Loopback, runner.port))
        }
    }

    @ParameterizedTest
    @MethodSource("transports")
    fun testSpecificAddresses(transport: Transport) = test {
        runWith(transport, IPv4Loopback, dualStack = true).use { runner ->
            assertTrue(canConnect(IPv4Loopback, runner.port))
            assertFalse(canConnect(IPv6Loopback, runner.port))
        }

        runWith(transport, IPv6Loopback, dualStack = true).use { runner ->
            assertFalse(canConnect(IPv4Loopback, runner.port))
            assertTrue(canConnect(IPv6Loopback, runner.port))
        }
    }

    @ParameterizedTest
    @MethodSource("transports")
    fun testPortInUse(transport: Transport) = test {
        val runner = NettyRunner(Content.from(Resource.fromString("Hello World!")), options(transport))

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

    @ParameterizedTest
    @MethodSource("transports")
    fun testRestart(transport: Transport) = test {
        NettyRunner.run(Content.from(Resource.fromString("Hello World!")), options(transport)).use { runner ->
            RawClient.connect(runner.port).use { before ->
                if (Platform.windows) {
                    // the JDK binds exclusively on Windows (SO_EXCLUSIVEADDRUSE), which does not allow to bind a port
                    // with connections in TIME_WAIT again, so the client closes the connection (and keeps TIME_WAIT)
                    RawClient.send(before, RawClient.request())

                    assertEquals("Hello World!", RawClient.readResponse(before.getInputStream()).text)
                } else {
                    // closed by the server, which leaves the connection in TIME_WAIT on the port of the server
                    RawClient.send(before, RawClient.request(headers = listOf("Connection" to "close")))

                    val input = before.getInputStream()

                    assertEquals("Hello World!", RawClient.readResponse(input).text)
                    assertEquals(-1, input.read())
                }
            }

            runner.host.restart()

            RawClient.connect(runner.port).use { after ->
                RawClient.send(after, RawClient.request())
                assertEquals("Hello World!", RawClient.readResponse(after.getInputStream()).text)
            }
        }
    }

    @Test
    fun testOnlyHttp1IsSupported() = test {
        NettyRunner(Content.from(Resource.fromString("Hello World!"))).use { runner ->
            runner.host.bind(null, runner.port, HttpProtocols.Http2)

            val e = assertFailsWith<UnsupportedOperationException> { runner.start() }

            assertEquals("The Netty engine does only support HTTP/1.1", e.message)
        }
    }

    // region Helpers

    private suspend fun runWith(transport: Transport, address: InetAddress?, dualStack: Boolean): NettyRunner {
        val runner = NettyRunner(Content.from(Resource.fromString("Hello World!")), options(transport))

        runner.host.bind(address, runner.port, HttpProtocols.Http1, dualStack)

        return runner.start()
    }

    private fun options(transport: Transport) = EngineOptions(eventLoop = EventLoopOptions(count = 2, transport = transport))

    /**
     * Checks whether the server answers a request sent via the given address (connections of clients of another
     * protocol family are accepted at the TCP level and closed immediately, see docs/DEVIATIONS.md).
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

    companion object {

        private val IPv4Loopback: InetAddress = InetAddress.getByName("127.0.0.1")

        private val IPv6Loopback: InetAddress = InetAddress.getByName("::1")

        @JvmStatic
        fun transports(): List<Transport> = Natives.transports

    }

    // endregion

}
