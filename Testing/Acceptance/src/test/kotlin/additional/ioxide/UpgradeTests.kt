package org.genhttp.testing.acceptance.additional.ioxide

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

import org.genhttp.api.protocol.SuspendingInput
import org.genhttp.modules.websockets.ReactiveConnection
import org.genhttp.modules.websockets.ReactiveHandler
import org.genhttp.modules.websockets.Websocket
import org.genhttp.modules.websockets.WebsocketFrame
import org.genhttp.testing.acceptance.additional.netty.RawClient
import org.genhttp.testing.acceptance.additional.netty.UpgradeHandler

import org.junit.jupiter.api.Test

import java.io.BufferedInputStream
import java.util.concurrent.ConcurrentHashMap

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Not ported from GenHTTP: the upgraded connections of the ioxide engine. Content that reads the input blocking (such
 * as the websocket tunnel of the reverse proxy) runs on a virtual thread, which waits until the reactor delivers data.
 * Content that supports it reads without blocking a thread by switching to the reactor of the connection and using
 * [SuspendingInput.readSuspending] (as the websockets module does); reading the input blocking on the reactor would
 * wait for data that only this reactor could deliver, so it fails instead of halting the reactor. Writes from other
 * threads are handed to the reactor.
 */
class UpgradeTests {

    @Test
    fun testBlockingReadsOnVirtualThread() = ioxideTest {
        val thread = CompletableDeferred<Thread>()

        val handler = UpgradeHandler { request, sink ->
            thread.complete(Thread.currentThread())

            val input = request.upgrade()

            // the stream is owned by the connection
            input.close()

            // single bytes, as well as arrays
            sink.stream.write(input.read())
            sink.stream.flush()

            val buffer = ByteArray(4096)

            while (true) {
                val read = input.read(buffer)

                if (read < 0) {
                    break
                }

                sink.stream.write(buffer, 0, read)
                sink.stream.flush()
            }
        }

        IoxideRunner.run(handler, IoxideRunner.reactors(1)).use { runner ->
            RawClient.connect(runner.port).use { client ->
                UpgradeHandler.upgrade(client)

                val input = client.getInputStream()

                RawClient.send(client, "Hello".toByteArray())
                assertEquals("Hello", String(RawClient.readExactly(input, 5)))

                // the reader is waiting for data now
                RawClient.send(client, " World!".toByteArray())
                assertEquals(" World!", String(RawClient.readExactly(input, 7)))

                // the end of the input ends the content and therefore the connection
                client.shutdownOutput()

                assertEquals(-1, input.read())
            }

            assertTrue(withTimeout(5.seconds) { thread.await() }.isVirtual, "Upgraded content did not run on a virtual thread")
        }
    }

    @Test
    fun testSuspendingReadsOnTheReactor() = ioxideTest {
        val reactors = ConcurrentHashMap.newKeySet<Thread>()

        val observed = ConcurrentHashMap.newKeySet<String>()

        val handler = UpgradeHandler { request, sink ->
            val input = request.upgrade()

            assertIs<SuspendingInput>(input)

            // also from another dispatcher, which hands the read to the reactor
            withContext(Dispatchers.Default) {
                observed.add("elsewhere: ${input.readSuspending(ByteArray(1), 0, 1)}")
            }

            withContext(input.dispatcher) {
                observed.add("on reactor: ${Thread.currentThread() in reactors}")

                val buffer = ByteArray(4096)

                observed.add("empty read: ${input.readSuspending(buffer, 0, 0)}")

                while (true) {
                    val read = input.readSuspending(buffer, 0, buffer.size)

                    if (read < 0) {
                        break
                    }

                    sink.stream.write(buffer, 0, read)
                    sink.stream.flush()
                }
            }
        }

        // a single reactor serves both connections, while their content waits for data
        IoxideRunner.run(handler, IoxideRunner.reactors(1), onReactorStart = { reactors.add(Thread.currentThread()) }).use { runner ->
            RawClient.connect(runner.port).use { first ->
                RawClient.connect(runner.port).use { second ->
                    UpgradeHandler.upgrade(first)
                    UpgradeHandler.upgrade(second)

                    // read elsewhere
                    RawClient.send(first, "!".toByteArray())
                    RawClient.send(second, "!".toByteArray())

                    for (round in 0 until 3) {
                        for ((index, client) in listOf(first, second).withIndex()) {
                            val message = "Message $round to $index"

                            RawClient.send(client, message.toByteArray())

                            assertEquals(message, String(RawClient.readExactly(client.getInputStream(), message.length)))
                        }
                    }

                    first.shutdownOutput()
                    second.shutdownOutput()

                    assertEquals(-1, first.getInputStream().read())
                    assertEquals(-1, second.getInputStream().read())
                }
            }

            assertEquals(setOf("elsewhere: 1", "on reactor: true", "empty read: 0"), observed.toSet())
        }
    }

    @Test
    fun testBlockingReadOnTheReactorFails() = ioxideTest {
        val failure = CompletableDeferred<Throwable?>()

        val handler = UpgradeHandler { request, sink ->
            val input = request.upgrade()

            if (input is SuspendingInput) {
                withContext(input.dispatcher) {
                    failure.complete(runCatching { input.read(ByteArray(16)) }.exceptionOrNull())
                }
            } else {
                failure.complete(IllegalStateException("The upgraded input is not a SuspendingInput"))
            }

            sink.stream.flush()
        }

        IoxideRunner.run(handler, IoxideRunner.reactors(1)).use { runner ->
            RawClient.connect(runner.port).use { client ->
                UpgradeHandler.upgrade(client)

                val error = withTimeout(10.seconds) { failure.await() }

                assertIs<IllegalStateException>(error)
                assertEquals("Upgraded connections cannot be read blocking on their reactor, use SuspendingInput.readSuspending()", error.message)

                // the content returned, which closes the upgraded connection
                assertEquals(-1, client.getInputStream().read())
            }

            // the (only) reactor is still serving connections
            RawClient.connect(runner.port).use { other ->
                UpgradeHandler.upgrade(other)
            }
        }
    }

    @Test
    fun testWritesFromOtherThreads() = ioxideTest {
        val handler = UpgradeHandler { request, sink ->
            val input = request.upgrade() as SuspendingInput

            val buffer = ByteArray(4096)

            withContext(input.dispatcher) {
                while (true) {
                    val read = input.readSuspending(buffer, 0, buffer.size)

                    if (read < 0) {
                        break
                    }

                    // answered by another thread, which waits until the reactor has sent the data
                    withContext(Dispatchers.IO) {
                        sink.stream.write(buffer, 0, read)
                        sink.stream.flush()
                    }
                }
            }
        }

        IoxideRunner.run(handler, IoxideRunner.reactors(1)).use { runner ->
            RawClient.connect(runner.port).use { client ->
                UpgradeHandler.upgrade(client)

                val input = BufferedInputStream(client.getInputStream())

                for (round in 0 until 5) {
                    val message = "Message $round"

                    RawClient.send(client, message.toByteArray())

                    assertEquals(message, String(RawClient.readExactly(input, message.length)))
                }
            }
        }
    }

    @Test
    fun testWebsocketsRunOnTheReactor() = ioxideTest {
        val reactors = ConcurrentHashMap.newKeySet<Thread>()

        val observed = ConcurrentHashMap.newKeySet<Boolean>()

        val websocket = Websocket.reactive().handler(object : ReactiveHandler {

            override suspend fun onMessage(connection: ReactiveConnection, message: WebsocketFrame) {
                observed.add(Thread.currentThread() in reactors)
                connection.write(message.data)
            }

        })

        IoxideRunner.run(websocket, IoxideRunner.reactors(1), onReactorStart = { reactors.add(Thread.currentThread()) }).use { runner ->
            RawClient.connect(runner.port).use { client ->
                RawClient.upgradeToWebsocket(client)

                val input = BufferedInputStream(client.getInputStream())

                for (index in 0 until 100) {
                    RawClient.send(client, RawClient.textFrame("Frame $index"))

                    assertEquals("Frame $index", RawClient.readTextFrame(input))
                }
            }

            assertEquals(setOf(true), observed.toSet())
        }
    }

}
