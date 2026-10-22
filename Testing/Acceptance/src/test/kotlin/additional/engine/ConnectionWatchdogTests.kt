package org.genhttp.testing.acceptance.additional.engine

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.withTimeout

import org.genhttp.api.content.io.Resource
import org.genhttp.engine.internal.infrastructure.ConnectionWatch
import org.genhttp.engine.internal.infrastructure.ConnectionWatchdog
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.fromString
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.Constants
import org.genhttp.testing.acceptance.test

import org.junit.jupiter.api.Test

import java.net.Socket
import java.nio.channels.Channel

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The watchdog replaces the per-read cancellation tokens of GenHTTP (see docs/DEVIATIONS.md).
 */
class ConnectionWatchdogTests {

    @Test
    fun testOverdueConnectionIsClosed() {
        val watchdog = ConnectionWatchdog()
        val connection = TestConnection()

        val watch = watchdog.register(connection)

        assertTrue(watch.arm(100))

        watchdog.check(99)

        assertTrue(connection.isOpen)
        assertFalse(watch.expired)

        watchdog.check(100)

        assertFalse(connection.isOpen)
        assertTrue(watch.expired)

        assertFalse(watch.disarm())
        assertFalse(watch.arm(200))
    }

    @Test
    fun testConnectionNotWaitingIsKeptOpen() {
        val watchdog = ConnectionWatchdog()
        val connection = TestConnection()

        val watch = watchdog.register(connection)

        assertTrue(watch.arm(100))
        assertTrue(watch.disarm())

        watchdog.check(1000)

        assertTrue(connection.isOpen)

        assertTrue(watch.arm(2000))
    }

    @Test
    fun testUnregisteredConnectionIsIgnored() {
        val watchdog = ConnectionWatchdog()
        val connection = TestConnection()

        val watch = watchdog.register(connection)

        assertTrue(watch.arm(100))

        watchdog.unregister(watch)
        watchdog.check(1000)

        assertTrue(connection.isOpen)
    }

    @Test
    fun testCloseExpiresWaitingConnections() {
        val watchdog = ConnectionWatchdog()

        val waiting = TestConnection()
        val busy = TestConnection()

        assertTrue(watchdog.register(waiting).arm(ConnectionWatch.deadlineAfter(1.hours)))

        val busyWatch = watchdog.register(busy)

        watchdog.close()

        assertFalse(waiting.isOpen)
        assertTrue(busy.isOpen)

        // connections finishing their current request are closed as soon as they wait for the next one
        assertFalse(busyWatch.arm(ConnectionWatch.deadlineAfter(1.hours)))
        assertFalse(busy.isOpen)
    }

    @Test
    fun testWatchdogChecksInInterval() = test {
        val watchdog = ConnectionWatchdog(10.milliseconds)
        val scope = CoroutineScope(Dispatchers.Default)

        try {
            watchdog.start(scope)

            val connection = TestConnection()

            assertTrue(watchdog.register(connection).arm(System.nanoTime()))

            withTimeout(5.seconds) {
                connection.closed.await()
            }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun testServerShutdownClosesIdleConnections() = test {
        val host = TestHost.run(Content.from(Resource.fromString("Hello World!")))

        host.use {
            Socket("127.0.0.1", host.port).use { client ->
                client.soTimeout = Constants.ClientTimeout

                val output = client.getOutputStream()

                output.write("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\nConnection: Keep-Alive\r\n\r\n".toByteArray(Charsets.US_ASCII))
                output.flush()

                val input = client.getInputStream()
                val response = StringBuilder()

                while (!response.endsWith("Hello World!")) {
                    val read = input.read()

                    assertTrue(read >= 0, "Connection closed before the response was read")

                    response.append(read.toChar())
                }

                host.close()

                // the connection waiting for the next request is closed by the server
                assertEquals(-1, input.read())
            }
        }
    }

    private class TestConnection : Channel {

        val closed = CompletableDeferred<Unit>()

        override fun isOpen(): Boolean = !closed.isCompleted

        override fun close() {
            closed.complete(Unit)
        }

    }

}
