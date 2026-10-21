package org.genhttp.testing.acceptance.engine

import org.genhttp.api.infrastructure.HttpProtocols
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.modules.layouting.Layout
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test

import org.junit.jupiter.api.Test

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket

import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BindingTests {

    // region Tests

    @MultiEngineTest
    fun testAnyIPv4NoDualStack(engine: ServerEngine) = test {
        runWith(ipv4Any, false, engine).use { runner ->
            assertTrue(canConnect(ipv4Loopback, runner.port))
            assertFalse(canConnect(ipv6Loopback, runner.port))
        }
    }

    @MultiEngineTest
    fun testAnyIPv4WithDualStack(engine: ServerEngine) = test {
        runWith(ipv4Any, true, engine).use { runner ->
            assertTrue(canConnect(ipv4Loopback, runner.port))
            assertTrue(canConnect(ipv6Loopback, runner.port))
        }
    }

    @Test
    fun testAnyIPv6NoDualStack() = test {
        runWith(ipv6Any, false, ServerEngine.Internal).use { runner ->
            assertFalse(canConnect(ipv4Loopback, runner.port))
            assertTrue(canConnect(ipv6Loopback, runner.port))
        }
    }

    @MultiEngineTest
    fun testAnyIPv6WithDualStack(engine: ServerEngine) = test {
        runWith(ipv6Any, true, engine).use { runner ->
            assertTrue(canConnect(ipv4Loopback, runner.port))
            assertTrue(canConnect(ipv6Loopback, runner.port))
        }
    }

    // the upstream test ignores the engine and runs with the default one
    @Suppress("UNUSED_PARAMETER")
    @MultiEngineTest
    fun testDualStackByDefault(engine: ServerEngine) = test {
        TestHost.run(Layout.create()).use { runner ->
            assertTrue(canConnect(ipv4Loopback, runner.port))
            assertTrue(canConnect(ipv6Loopback, runner.port))
        }
    }

    // endregion

    // region Helpers

    private val ipv4Any: InetAddress = InetAddress.getByName("0.0.0.0")

    private val ipv6Any: InetAddress = InetAddress.getByName("::")

    private val ipv4Loopback: InetAddress = InetAddress.getByName("127.0.0.1")

    private val ipv6Loopback: InetAddress = InetAddress.getByName("::1")

    private suspend fun runWith(ip: InetAddress?, dualStack: Boolean, engine: ServerEngine): TestHost {
        val runner = TestHost(Layout.create().build(), engine = engine)

        runner.host.bind(ip, runner.port, HttpProtocols.All, dualStack)

        runner.start()

        return runner
    }

    /**
     * Checks whether the server can be reached via the given address. The JDK cannot create IPv6-only sockets,
     * so the engine accepts IPv4 connections on such endpoints at the TCP level but closes them immediately.
     * Therefore, this check requires the server to actually answer a request (see PORTING.md).
     */
    private fun canConnect(address: InetAddress, port: Int, timeoutMs: Int = 500): Boolean {
        try {
            Socket().use { client ->
                client.connect(InetSocketAddress(address, port), timeoutMs)
                client.soTimeout = 2000

                val request = listOf("GET / HTTP/1.1", "Host: localhost", "Connection: close", "", "").joinToString("\r\n")

                client.getOutputStream().write(request.toByteArray(Charsets.US_ASCII))
                client.getOutputStream().flush()

                val buffer = ByteArray(8)
                val read = client.getInputStream().readNBytes(buffer, 0, buffer.size)

                return read > 0 && String(buffer, 0, read, Charsets.US_ASCII).startsWith("HTTP/1.")
            }
        } catch (_: Exception) {
            // nop
        }

        return false
    }

    // endregion

}
