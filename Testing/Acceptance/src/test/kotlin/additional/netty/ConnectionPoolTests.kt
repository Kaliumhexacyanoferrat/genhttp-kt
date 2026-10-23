package org.genhttp.testing.acceptance.additional.netty

import kotlinx.coroutines.delay

import org.genhttp.api.protocol.getEntry
import org.genhttp.engine.netty.EngineOptions
import org.genhttp.engine.netty.EventLoopOptions
import org.genhttp.modules.io.content
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.AsyncFunctionalHandler

import org.junit.jupiter.api.Test

import org.slf4j.event.Level

import java.io.BufferedInputStream
import java.lang.management.ManagementFactory
import java.util.zip.CRC32

import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Not ported from GenHTTP: the Netty engine reuses the state of the connections of an event loop (request, reader
 * with its buffer, writer, driver) for the next connections, like ioxide reuses its requests per reactor. A
 * connection must never see anything of the connection served before with the same state, whether that one ended
 * regularly, was abandoned by its client, grew its buffer for a large body or was reset in the middle of a pipelined
 * batch. Neither may the pool keep the memory a connection needed for a large body. A single event loop is used, so
 * that all connections share the same pool.
 */
class ConnectionPoolTests {

    @Test
    fun testShortConnections() = test {
        NettyRunner.run(EchoHandler, SingleLoop).use { runner ->
            for (index in 0 until 200) {
                RawClient.connect(runner.port).use { client ->
                    RawClient.send(client, RawClient.request("/$index", headers = listOf("Connection" to "close", "X-Client" to "$index")))

                    // some clients go away without waiting for their response
                    if (index % 10 != 9) {
                        val input = client.getInputStream()

                        assertEquals("GET /$index $index", RawClient.readResponse(input).text)

                        assertEquals(-1, input.read())
                    }
                }
            }
        }
    }

    @Test
    fun testLargeBodiesFollowedBySmallRequests() = test {
        val large = Random(42).nextBytes(4 * 1024 * 1024)

        NettyRunner.run(EchoHandler, SingleLoop).use { runner ->
            RawClient.connect(runner.port).use { client ->
                val input = BufferedInputStream(client.getInputStream())

                RawClient.send(client, RawClient.request("/large", "POST", large))
                assertEquals("POST /large ${large.size} ${checksum(large)}", RawClient.readResponse(input).text)

                for (index in 0 until 5) {
                    RawClient.send(client, RawClient.request("/small/$index"))
                    assertEquals("GET /small/$index -", RawClient.readResponse(input).text)
                }

                // a large body followed by a small request, received at once
                RawClient.send(client, RawClient.request("/large", "POST", large) + RawClient.request("/after"))

                assertEquals("POST /large ${large.size} ${checksum(large)}", RawClient.readResponse(input).text)
                assertEquals("GET /after -", RawClient.readResponse(input).text)
            }

            // a connection that ends right after a large body hands its state back to the pool
            RawClient.connect(runner.port).use { client ->
                RawClient.send(client, RawClient.request("/large", "POST", large, listOf("Connection" to "close")))

                val input = BufferedInputStream(client.getInputStream())

                assertEquals("POST /large ${large.size} ${checksum(large)}", RawClient.readResponse(input).text)
                assertEquals(-1, input.read())
            }

            // the following connections reuse the state
            for (index in 0 until 3) {
                RawClient.connect(runner.port).use { client ->
                    val body = Random(index).nextBytes(100)

                    RawClient.send(client, RawClient.request("/next", "POST", body) + RawClient.request("/next/$index"))

                    val input = BufferedInputStream(client.getInputStream())

                    assertEquals("POST /next ${body.size} ${checksum(body)}", RawClient.readResponse(input).text)
                    assertEquals("GET /next/$index -", RawClient.readResponse(input).text)
                }
            }
        }
    }

    @Test
    fun testLargeBodyIsNotKeptByThePool() = test {
        val handler = AsyncFunctionalHandler { request -> request.respond().content("Received").build() }

        NettyRunner.run(handler, SingleLoop).use { runner ->
            // the pool holds the state of a connection now
            exchange(runner.port, RawClient.request(headers = listOf("Connection" to "close")))

            val baseline = usedHeap()

            // the reader of the connection grows to buffer the body, the connection ends with the request
            exchange(runner.port, RawClient.request("/", "POST", ByteArray(64 * 1024 * 1024), listOf("Connection" to "close")))

            // the next connection is served with the state of the previous one
            exchange(runner.port, RawClient.request(headers = listOf("Connection" to "close")))

            val retained = usedHeap() - baseline

            assertTrue(retained < 32 * 1024 * 1024, "The pooled state of the closed connections holds ${retained / 1024 / 1024} MB more than before")
        }
    }

    @Test
    fun testClientResetDuringPipelinedBatch() = test {
        val handler = AsyncFunctionalHandler { request ->
            // the batch is still being served when the client goes away
            delay(2.milliseconds)

            request.respond().content(request.header.path.toString()).build()
        }

        NettyRunner.run(handler, SingleLoop).use { runner ->
            for (attempt in 0 until 5) {
                RawClient.connect(runner.port).use { client ->
                    val batch = (0 until 50).map { RawClient.request("/$attempt/$it") }.reduce(ByteArray::plus)

                    RawClient.send(client, batch)

                    val input = client.getInputStream()

                    for (index in 0 until 3) {
                        assertEquals("/$attempt/$index", RawClient.readResponse(input).text)
                    }

                    // resets the connection instead of closing it
                    client.setSoLinger(true, 0)
                }
            }

            RawClient.connect(runner.port).use { client ->
                val batch = (0 until 20).map { RawClient.request("/after/$it") }.reduce(ByteArray::plus)

                RawClient.send(client, batch)

                val input = BufferedInputStream(client.getInputStream())

                for (index in 0 until 20) {
                    assertEquals("/after/$index", RawClient.readResponse(input).text)
                }
            }

            val problems = runner.logging.entries.filter { it.level == Level.WARN || it.level == Level.ERROR }

            assertTrue(problems.isEmpty(), "Disconnected clients have been logged: ${problems.map { "${it.message}: ${it.throwable}" }}")
        }
    }

    // region Helpers

    private companion object {

        val SingleLoop = EngineOptions(eventLoop = EventLoopOptions(count = 1))

        /**
         * Answers with the method, the path and either the length and checksum of the body or the X-Client header.
         */
        val EchoHandler = AsyncFunctionalHandler { request ->
            val header = request.header

            val details = if (request.hasBody) {
                val body = request.getBody()!!.asStream().readAllBytes()
                "${body.size} ${checksum(body)}"
            } else {
                header.headers.getEntry("X-Client") ?: "-"
            }

            request.respond().content("${header.method} ${header.path} $details").build()
        }

        fun checksum(data: ByteArray): Long = CRC32().apply { update(data) }.value

        /**
         * Sends the given request on a new connection and waits until the server closed the connection.
         */
        fun exchange(port: Int, request: ByteArray) {
            RawClient.connect(port).use { client ->
                RawClient.send(client, request)

                val input = BufferedInputStream(client.getInputStream())

                assertEquals("Received", RawClient.readResponse(input).text)
                assertEquals(-1, input.read())
            }
        }

        /**
         * The heap used by live objects.
         */
        fun usedHeap(): Long {
            System.gc()
            return ManagementFactory.getMemoryMXBean().heapMemoryUsage.used
        }

    }

    // endregion

}
