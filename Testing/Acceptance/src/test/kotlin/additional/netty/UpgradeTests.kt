package org.genhttp.testing.acceptance.additional.netty

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

import org.genhttp.api.protocol.SuspendingInput
import org.genhttp.engine.netty.EngineOptions
import org.genhttp.engine.netty.EventLoopOptions
import org.genhttp.testing.acceptance.test

import org.junit.jupiter.api.Test

import java.util.concurrent.ConcurrentHashMap

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Not ported from GenHTTP: the input of an upgraded connection on the Netty engine. Content that reads it blocking
 * (such as the websocket tunnel of the reverse proxy) runs on a virtual thread, which waits until the event loop
 * delivers data. Content that supports it reads without blocking a thread by switching to the loop of the connection
 * and using [SuspendingInput.readSuspending]; reading the input blocking on the loop would wait for data that only
 * this loop could deliver, so it fails instead of deadlocking the loop.
 */
class UpgradeTests {

    @Test
    fun testBlockingReadsOnVirtualThread() = test {
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

        NettyRunner.run(handler, options(1)).use { runner ->
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
    fun testSuspendingReadsOnTheLoop() = test {
        val loops = ConcurrentHashMap.newKeySet<Thread>()

        val observed = ConcurrentHashMap.newKeySet<String>()

        val handler = UpgradeHandler { request, sink ->
            val input = request.upgrade()

            assertIs<SuspendingInput>(input)

            withContext(input.dispatcher) {
                observed.add("on loop: ${Thread.currentThread() in loops}")

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

        // a single loop serves both connections, while their content waits for data
        NettyRunner.run(handler, options(1), onEventLoopStart = { loops.add(Thread.currentThread()) }).use { runner ->
            RawClient.connect(runner.port).use { first ->
                RawClient.connect(runner.port).use { second ->
                    UpgradeHandler.upgrade(first)
                    UpgradeHandler.upgrade(second)

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

            assertEquals(setOf("on loop: true", "empty read: 0"), observed.toSet())
        }
    }

    @Test
    fun testBlockingReadOnTheLoopFails() = test {
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

        NettyRunner.run(handler, options(1)).use { runner ->
            RawClient.connect(runner.port).use { client ->
                UpgradeHandler.upgrade(client)

                val error = withTimeout(10.seconds) { failure.await() }

                assertIs<IllegalStateException>(error)
                assertEquals("Upgraded connections cannot be read blocking on their event loop, use SuspendingInput.readSuspending()", error.message)

                // the content returned, which closes the upgraded connection
                assertEquals(-1, client.getInputStream().read())
            }

            // the (only) loop is still serving connections
            RawClient.connect(runner.port).use { other ->
                UpgradeHandler.upgrade(other)
            }
        }
    }

    // region Helpers

    private fun options(count: Int) = EngineOptions(eventLoop = EventLoopOptions(count = count))

    // endregion

}
