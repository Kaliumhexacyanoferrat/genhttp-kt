package org.genhttp.testing.acceptance.additional.netty

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.engine.netty.EngineOptions
import org.genhttp.engine.netty.EventLoopOptions
import org.genhttp.engine.netty.Transport
import org.genhttp.modules.io.content
import org.genhttp.testing.acceptance.test

import org.junit.jupiter.api.Test

import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Not ported from GenHTTP: the transports of the Netty engine (epoll, io_uring, NIO and the automatic choice) and the
 * number of event loops. With the native transports, every loop accepts on a socket of its own; with NIO, a single
 * socket accepts the connections and hands them to the loops one after another. Handlers always run on the loop
 * that serves the connection.
 */
class TransportTests {

    @Test
    fun testEpoll() = test {
        assertServes(Transport.Epoll, "Epoll")
    }

    @Test
    fun testIoUring() = test {
        if (isNativeAvailable(IoUring)) {
            assertServes(Transport.IoUring, "IoUring")
        } else {
            // a transport that has been requested explicitly is not silently replaced by another one
            NettyRunner(LoopHandler(), options(Transport.IoUring, 2)).use { runner ->
                val e = assertFailsWith<UnsupportedOperationException> { runner.start() }

                assertEquals("The io_uring transport is not available on this system", e.message)
            }
        }
    }

    @Test
    fun testNio() = test {
        val threads = assertServes(Transport.Nio, "Nio")

        // the connections are handed to the loops one after another
        assertEquals(2, threads.size)
    }

    @Test
    fun testAutoPrefersEpoll() = test {
        assertServes(Transport.Auto, if (isNativeAvailable(Epoll)) "Epoll" else "Nio")
    }

    @Test
    fun testSingleEventLoop() = test {
        val threads = assertServes(Transport.Auto, if (isNativeAvailable(Epoll)) "Epoll" else "Nio", count = 1)

        assertEquals(1, threads.size)
    }

    @Test
    fun testEventLoopsAreRequired() {
        val none = assertFailsWith<IllegalArgumentException> { EventLoopOptions(count = 0) }

        assertEquals("At least one event loop is required", none.message)

        assertFailsWith<IllegalArgumentException> { EventLoopOptions(count = -1) }
    }

    @Test
    fun testDefaults() {
        val options = EngineOptions()

        assertEquals(Runtime.getRuntime().availableProcessors(), options.eventLoop.count)
        assertEquals(Transport.Auto, options.eventLoop.transport)
    }

    // region Helpers

    /**
     * Runs a server with the given transport and event loops, sends requests on several connections at once and
     * checks that all of them have been served by the loops of the server.
     *
     * @return The threads of the loops that served the requests
     */
    private suspend fun assertServes(transport: Transport, expected: String, count: Int = 2): Set<Thread> {
        val loops = ConcurrentHashMap.newKeySet<Thread>()

        val handler = LoopHandler()

        NettyRunner(handler, options(transport, count), onEventLoopStart = { loops.add(Thread.currentThread()) }).use { runner ->
            runner.start()

            assertEquals(count, loops.size)

            val settings = "Transport: $expected, EventLoops: $count"

            assertTrue(runner.logging.messages.any { it.contains(settings) }, "'$settings' not found in ${runner.logging.messages}")

            val clients = (0 until 8).map { RawClient.connect(runner.port) }

            try {
                for (round in 0 until 3) {
                    for ((index, client) in clients.withIndex()) {
                        RawClient.send(client, RawClient.request("/$index/$round"))
                    }

                    for ((index, client) in clients.withIndex()) {
                        val response = RawClient.readResponse(client.getInputStream())

                        assertEquals(200, response.status)
                        assertEquals("/$index/$round", response.text)
                    }
                }
            } finally {
                clients.forEach(Socket::close)
            }

            assertTrue(loops.containsAll(handler.threads), "Requests have been served by ${handler.threads} instead of the loops $loops")

            return handler.threads.toSet()
        }
    }

    private fun options(transport: Transport, count: Int) = EngineOptions(eventLoop = EventLoopOptions(count = count, transport = transport))

    /**
     * Echoes the requested path and records the threads the requests have been handled on.
     */
    private class LoopHandler : Handler {

        val threads: MutableSet<Thread> = ConcurrentHashMap.newKeySet()

        override suspend fun prepare(server: Server) {
        }

        override suspend fun handle(request: Request): Response {
            threads.add(Thread.currentThread())

            return request.respond().content(request.header.path.toString()).build()
        }

    }

    private companion object {

        const val Epoll = "io.netty.channel.epoll.Epoll"

        const val IoUring = "io.netty.channel.uring.IoUring"

    }

    // endregion

}
