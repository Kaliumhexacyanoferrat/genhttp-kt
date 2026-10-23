package org.genhttp.testing.acceptance.additional.netty

import io.netty.channel.EventLoop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

import org.genhttp.api.content.io.Resource
import org.genhttp.engine.netty.EngineOptions
import org.genhttp.engine.netty.EventLoopOptions
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.content
import org.genhttp.modules.io.fromString
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.AsyncFunctionalHandler

import org.junit.jupiter.api.Test

import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Not ported from GenHTTP: the start hook of the Netty engine (`onReactorStart` of ioxide) runs once per event loop,
 * on that loop, before the server accepts connections, and a hook that fails stops the server from starting.
 * Handlers run on the loop that serves their connection: they return to it after suspending on another dispatcher,
 * their timers are scheduled on it, and a loop serves other connections while handlers are suspended.
 */
class EventLoopTests {

    @Test
    fun testHookRunsOncePerLoopBeforeAccepting() = test {
        val calls = CopyOnWriteArrayList<HookCall>()

        var port = 0

        val runner = NettyRunner(Content.from(Resource.fromString("Hello World!")), options(3), onEventLoopStart = { loop ->
            calls.add(HookCall(loop, Thread.currentThread(), loop.inEventLoop(), accepts(port)))
        })

        port = runner.port

        runner.use {
            runner.start()

            assertEquals(3, calls.size)

            assertEquals(3, calls.map { it.loop }.toSet().size, "The hook ran more than once on a loop")
            assertEquals(3, calls.map { it.thread }.toSet().size)

            assertTrue(calls.all { it.onLoop }, "The hook did not run on the loop it has been passed")
            assertTrue(calls.none { it.accepting }, "The server accepted connections before all loops have been started")

            assertTrue(accepts(runner.port))
        }
    }

    @Test
    fun testFailingHookStopsTheStart() = test {
        val loops = CopyOnWriteArrayList<EventLoop>()

        NettyRunner(Content.from(Resource.fromString("Hello World!")), options(2), onEventLoopStart = { loop ->
            loops.add(loop)
            throw IllegalStateException("The loop cannot be prepared")
        }).use { runner ->
            val e = assertFailsWith<IllegalStateException> { runner.start() }

            assertEquals("The loop cannot be prepared", e.message)

            assertFalse(accepts(runner.port))

            assertTrue(loops.isNotEmpty())
            assertTrue(loops.all { it.isShuttingDown }, "The loops of the failed server are still running")
        }
    }

    @Test
    fun testHandlerReturnsToItsLoop() = test {
        val loops = ConcurrentHashMap.newKeySet<Thread>()

        val handler = AsyncFunctionalHandler { request ->
            val loop = Thread.currentThread()

            val elsewhere = withContext(Dispatchers.Default) { Thread.currentThread() }

            val resumed = Thread.currentThread()

            request.respond().content("${loop in loops} ${elsewhere !in loops} ${resumed === loop}").build()
        }

        NettyRunner.run(handler, options(2), onEventLoopStart = { loops.add(Thread.currentThread()) }).use { runner ->
            RawClient.connect(runner.port).use { client ->
                repeat(3) {
                    RawClient.send(client, RawClient.request())

                    assertEquals("true true true", RawClient.readResponse(client.getInputStream()).text)
                }
            }
        }
    }

    @Test
    fun testTimersRunOnTheLoop() = test {
        val handler = AsyncFunctionalHandler { request ->
            val loop = Thread.currentThread()

            delay(10.milliseconds)

            val afterDelay = Thread.currentThread() === loop

            val expired = withTimeoutOrNull(20.milliseconds) {
                delay(10.seconds)
                "not expired"
            } == null

            val afterTimeout = Thread.currentThread() === loop

            val completed = withTimeout(10.seconds) {
                delay(1.milliseconds)
                "completed"
            }

            request.respond().content("$afterDelay $expired $afterTimeout $completed ${Thread.currentThread() === loop}").build()
        }

        NettyRunner.run(handler, options(1)).use { runner ->
            RawClient.connect(runner.port).use { client ->
                RawClient.send(client, RawClient.request())

                assertEquals("true true true completed true", RawClient.readResponse(client.getInputStream()).text)
            }
        }
    }

    @Test
    fun testLoopServesOtherConnectionsWhileHandlersAreSuspended() = test {
        val handler = AsyncFunctionalHandler { request ->
            delay(300.milliseconds)
            request.respond().content(request.header.path.toString()).build()
        }

        NettyRunner.run(handler, options(1)).use { runner ->
            val clients = (0 until 20).map { RawClient.connect(runner.port) }

            try {
                val start = TimeSource.Monotonic.markNow()

                for ((index, client) in clients.withIndex()) {
                    RawClient.send(client, RawClient.request("/$index"))
                }

                for ((index, client) in clients.withIndex()) {
                    assertEquals("/$index", RawClient.readResponse(client.getInputStream()).text)
                }

                // one after another, the requests would take 6 seconds
                assertTrue(start.elapsedNow() < 4.seconds, "The requests have not been handled concurrently (${start.elapsedNow()})")
            } finally {
                clients.forEach(Socket::close)
            }
        }
    }

    // region Helpers

    private class HookCall(val loop: EventLoop, val thread: Thread, val onLoop: Boolean, val accepting: Boolean)

    private fun options(count: Int) = EngineOptions(eventLoop = EventLoopOptions(count = count))

    /**
     * Whether a connection to the given port can be established (refused connections take a while on Windows, which
     * retries them, so the attempt is bounded).
     */
    private fun accepts(port: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 1000) }
    }.isSuccess

    // endregion

}
