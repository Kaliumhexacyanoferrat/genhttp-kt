package org.genhttp.testing.acceptance.additional.ioxide

import ioxide.Reactor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield

import org.genhttp.api.content.io.Resource
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.content
import org.genhttp.modules.io.fromString
import org.genhttp.testing.acceptance.additional.netty.RawClient
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
 * Not ported from GenHTTP: the start hook of the ioxide engine (`onReactorStart`) runs once per reactor, on its
 * thread, before the reactor accepts connections (its listeners are bound already, so connections wait in their
 * backlog), and a hook that fails stops the server from starting. Handlers run
 * on the reactor that serves their connection: they return to it after suspending on another dispatcher, their
 * timers are submitted to its ring, coroutines they launch run on it, and a reactor serves other connections while
 * handlers are suspended.
 */
class ReactorTests {

    @Test
    fun testHookRunsOncePerReactorBeforeServing() = ioxideTest {
        val calls = CopyOnWriteArrayList<HookCall>()

        val runner = IoxideRunner(Content.from(Resource.fromString("Hello World!")), IoxideRunner.reactors(3), onReactorStart = { reactor ->
            calls.add(HookCall(reactor, Thread.currentThread(), reactor.onReactorThread))
        })

        runner.use {
            runner.start()

            assertEquals(3, calls.size)

            assertEquals(3, calls.map { it.reactor }.toSet().size, "The hook ran more than once on a reactor")
            assertEquals(3, calls.map { it.thread }.toSet().size)

            assertTrue(calls.all { it.onReactor }, "The hook did not run on the reactor it has been passed")

            RawClient.connect(runner.port).use { client ->
                RawClient.send(client, RawClient.request())

                assertEquals("Hello World!", RawClient.readResponse(client.getInputStream()).text)
            }
        }
    }

    @Test
    fun testFailingHookStopsTheStart() = ioxideTest {
        val threads = CopyOnWriteArrayList<Thread>()

        IoxideRunner(Content.from(Resource.fromString("Hello World!")), IoxideRunner.reactors(2), onReactorStart = {
            threads.add(Thread.currentThread())
            throw IllegalStateException("The reactor cannot be prepared")
        }).use { runner ->
            val e = assertFailsWith<IllegalStateException> { runner.start() }

            assertEquals("The reactor cannot be prepared", e.message)

            assertFalse(accepts(runner.port))

            assertTrue(threads.isNotEmpty())
            assertTrue(threads.none { it.isAlive }, "The reactors of the failed server are still running")
        }
    }

    @Test
    fun testHandlerReturnsToItsReactor() = ioxideTest {
        val reactors = ConcurrentHashMap.newKeySet<Thread>()

        val handler = AsyncFunctionalHandler { request ->
            val reactor = Thread.currentThread()

            val elsewhere = withContext(Dispatchers.Default) { Thread.currentThread() }

            val resumed = Thread.currentThread()

            yield()

            val yielded = Thread.currentThread()

            request.respond().content("${reactor in reactors} ${elsewhere !in reactors} ${resumed === reactor} ${yielded === reactor}").build()
        }

        IoxideRunner.run(handler, onReactorStart = { reactors.add(Thread.currentThread()) }).use { runner ->
            RawClient.connect(runner.port).use { client ->
                repeat(3) {
                    RawClient.send(client, RawClient.request())

                    assertEquals("true true true true", RawClient.readResponse(client.getInputStream()).text)
                }
            }
        }
    }

    @Test
    fun testTimersRunOnTheReactor() = ioxideTest {
        val handler = AsyncFunctionalHandler { request ->
            val reactor = Thread.currentThread()

            delay(10.milliseconds)

            val afterDelay = Thread.currentThread() === reactor

            val expired = withTimeoutOrNull(20.milliseconds) {
                delay(10.seconds)
                "not expired"
            } == null

            val afterTimeout = Thread.currentThread() === reactor

            val completed = withTimeout(10.seconds) {
                delay(1.milliseconds)
                "completed"
            }

            request.respond().content("$afterDelay $expired $afterTimeout $completed ${Thread.currentThread() === reactor}").build()
        }

        IoxideRunner.run(handler, IoxideRunner.reactors(1)).use { runner ->
            RawClient.connect(runner.port).use { client ->
                RawClient.send(client, RawClient.request())

                assertEquals("true true true completed true", RawClient.readResponse(client.getInputStream()).text)
            }
        }
    }

    @Test
    fun testCoroutinesOfHandlersRunOnTheReactor() = ioxideTest {
        val handler = AsyncFunctionalHandler { request ->
            val reactor = Thread.currentThread()

            val threads = CopyOnWriteArrayList<Thread>()

            val result = coroutineScope {
                launch {
                    delay(5.milliseconds)
                    threads.add(Thread.currentThread())
                }

                async { Thread.currentThread() }.await()
            }

            request.respond().content("${result === reactor} ${threads.all { it === reactor }}").build()
        }

        IoxideRunner.run(handler, IoxideRunner.reactors(1)).use { runner ->
            RawClient.connect(runner.port).use { client ->
                RawClient.send(client, RawClient.request())

                assertEquals("true true", RawClient.readResponse(client.getInputStream()).text)
            }
        }
    }

    @Test
    fun testReactorServesOtherConnectionsWhileHandlersAreSuspended() = ioxideTest {
        val handler = AsyncFunctionalHandler { request ->
            delay(300.milliseconds)
            request.respond().content(request.header.path.toString()).build()
        }

        IoxideRunner.run(handler, IoxideRunner.reactors(1)).use { runner ->
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

    private class HookCall(val reactor: Reactor, val thread: Thread, val onReactor: Boolean)

    /**
     * Whether a connection to the given port can be established.
     */
    private fun accepts(port: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 1000) }
    }.isSuccess

    // endregion

}
