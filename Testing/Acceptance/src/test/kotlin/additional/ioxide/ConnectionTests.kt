package org.genhttp.testing.acceptance.additional.ioxide

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout

import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseSink
import org.genhttp.api.protocol.getEntry
import org.genhttp.engine.ioxide.EngineOptions
import org.genhttp.engine.ioxide.ReactorOptions
import org.genhttp.engine.ioxide.TcpTransportOptions
import org.genhttp.modules.io.content
import org.genhttp.testing.acceptance.additional.netty.RawClient
import org.genhttp.testing.acceptance.utilities.AsyncFunctionalHandler

import org.junit.jupiter.api.Test

import java.io.BufferedInputStream
import java.nio.ByteBuffer

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Not ported from GenHTTP: how the ioxide engine serves a connection. Pipelined requests are answered in order,
 * requests may arrive in any number of receives, large request bodies are buffered (and the grown buffer is not kept
 * for the connection), large responses are sent completely (with a known length or chunked), data written after the
 * handler suspended is sent without waiting for the end of the response, and the state of finished connections is
 * reused for the next ones.
 */
class ConnectionTests {

    @Test
    fun testPipelinedRequestsAreAnsweredInOrder() = ioxideTest {
        val handler = AsyncFunctionalHandler { request ->
            request.respond().content("Request ${request.header.headers.getEntry("X-Index")}").build()
        }

        IoxideRunner.run(handler).use { runner ->
            RawClient.connect(runner.port).use { client ->
                val requests = (0 until 200).map { RawClient.request(headers = listOf("X-Index" to "$it")) }

                // sent at once, so that they arrive together
                RawClient.send(client, requests.reduce { a, b -> a + b })

                val input = BufferedInputStream(client.getInputStream())

                for (index in 0 until 200) {
                    assertEquals("Request $index", RawClient.readResponse(input).text)
                }
            }
        }
    }

    @Test
    fun testRequestsArriveInPieces() = ioxideTest {
        val handler = AsyncFunctionalHandler { request ->
            val body = request.getBody()?.asStream()?.readAllBytes()?.toString(Charsets.UTF_8) ?: "none"
            request.respond().content("${request.header.path} $body").build()
        }

        IoxideRunner.run(handler).use { runner ->
            RawClient.connect(runner.port).use { client ->
                client.tcpNoDelay = true

                val request = RawClient.request("/pieces", "POST", "Hello World!".toByteArray())

                for (byte in request) {
                    client.getOutputStream().write(byte.toInt())
                    client.getOutputStream().flush()
                    Thread.sleep(1)
                }

                assertEquals("/pieces Hello World!", RawClient.readResponse(client.getInputStream()).text)
            }
        }
    }

    @Test
    fun testLargeRequestBodiesAreBuffered() = ioxideTest {
        val handler = AsyncFunctionalHandler { request ->
            val body = request.getBody()?.asStream()?.readAllBytes()

            request.respond().content("${body?.size ?: 0} ${body?.contentEquals(Large.copyOf(body.size)) ?: false}").build()
        }

        IoxideRunner.run(handler).use { runner ->
            RawClient.connect(runner.port).use { client ->
                val input = BufferedInputStream(client.getInputStream())

                RawClient.send(client, RawClient.request("/", "POST", Large))
                assertEquals("${Large.size} true", RawClient.readResponse(input).text)

                RawClient.send(client, chunked(Large))
                assertEquals("${Large.size} true", RawClient.readResponse(input).text)

                // the connection continues with a buffer of the default size
                RawClient.send(client, RawClient.request("/", "POST", "small".toByteArray()))
                assertEquals("5 false", RawClient.readResponse(input).text)
            }
        }
    }

    @Test
    fun testLargeResponsesAreSentCompletely() = ioxideTest {
        val handler = AsyncFunctionalHandler { request ->
            if (request.header.path.toString() == "/chunked") {
                request.respond().content(UnknownLength(Large)).build()
            } else {
                request.respond().content(Large, ContentType.ApplicationOctetStream).build()
            }
        }

        IoxideRunner.run(handler).use { runner ->
            // a small receive buffer, so that the server has to wait for the client
            RawClient.connect(runner.port, receiveBuffer = 8192).use { client ->
                val input = BufferedInputStream(client.getInputStream())

                for (path in listOf("/known", "/chunked", "/known")) {
                    RawClient.send(client, RawClient.request(path))

                    assertTrue(Large.contentEquals(RawClient.readResponse(input).body), "The response to $path is not complete")
                }
            }
        }
    }

    @Test
    fun testDataWrittenAfterSuspendingIsSent() = ioxideTest {
        val proceed = CompletableDeferred<Unit>()

        val handler = AsyncFunctionalHandler { request -> request.respond().content(Stepwise(proceed)).build() }

        IoxideRunner.run(handler).use { runner ->
            RawClient.connect(runner.port).use { client ->
                val input = BufferedInputStream(client.getInputStream())

                RawClient.send(client, RawClient.request())

                val head = RawClient.readHead(input)

                assertEquals(200, head.status)

                // the first chunk is sent while the content waits for the second one
                assertEquals("00000005\r\nfirst\r\n", String(RawClient.readExactly(input, 17), Charsets.US_ASCII))

                proceed.complete(Unit)

                assertEquals("00000006\r\nsecond\r\n0\r\n\r\n", String(RawClient.readExactly(input, 23), Charsets.US_ASCII))
            }
        }
    }

    @Test
    fun testConnectionsAreReused() = ioxideTest {
        val handler = AsyncFunctionalHandler { request -> request.respond().content("Hello World!").build() }

        // a single reactor serves (and pools) every connection
        IoxideRunner.run(handler, EngineOptions(reactor = ReactorOptions(reactorCount = 1), tcp = TcpTransportOptions(poolMax = 4))).use { runner ->
            repeat(200) { index ->
                RawClient.connect(runner.port).use { client ->
                    val close = index % 2 == 0

                    RawClient.send(client, RawClient.request(headers = if (close) listOf("Connection" to "close") else emptyList()))

                    val input = client.getInputStream()

                    assertEquals("Hello World!", RawClient.readResponse(input).text)

                    if (close) {
                        assertEquals(-1, input.read())
                    }
                }
            }
        }
    }

    @Test
    fun testHttp10ClosesTheConnection() = ioxideTest {
        val handler = AsyncFunctionalHandler { request -> request.respond().content("Hello World!").build() }

        IoxideRunner.run(handler).use { runner ->
            RawClient.connect(runner.port).use { client ->
                RawClient.send(client, "GET / HTTP/1.0\r\nHost: localhost\r\n\r\n".toByteArray(Charsets.US_ASCII))

                val input = client.getInputStream()

                assertEquals("Hello World!", RawClient.readResponse(input).text)
                assertEquals(-1, input.read())
            }
        }
    }

    // region Helpers

    private fun chunked(data: ByteArray): ByteArray {
        val head = "POST / HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\n\r\n".toByteArray(Charsets.US_ASCII)

        val chunks = data.toList().chunked(100_000).joinToString("") { chunk -> "${chunk.size.toString(16)}\r\n${String(chunk.toByteArray(), Charsets.ISO_8859_1)}\r\n" }

        return head + chunks.toByteArray(Charsets.ISO_8859_1) + "0\r\n\r\n".toByteArray(Charsets.US_ASCII)
    }

    /**
     * Content of unknown length (sent chunked).
     */
    private class UnknownLength(private val data: ByteArray) : ResponseContent {

        override val length: Long? = null

        override val type: ContentType = ContentType.ApplicationOctetStream

        override val encoding: ByteBuffer? = null

        override suspend fun calculateChecksum(): Long? = null

        override suspend fun write(sink: ResponseSink) {
            sink.writer.write(ByteBuffer.wrap(data))
        }

    }

    /**
     * Writes a chunk, waits until it may continue and writes another one.
     */
    private class Stepwise(private val proceed: CompletableDeferred<Unit>) : ResponseContent {

        override val length: Long? = null

        override val type: ContentType = ContentType.TextPlain

        override val encoding: ByteBuffer? = null

        override suspend fun calculateChecksum(): Long? = null

        override suspend fun write(sink: ResponseSink) {
            sink.stream.write("first".toByteArray())
            sink.stream.flush()

            withTimeout(10.seconds) { proceed.await() }

            delay(10.milliseconds)

            sink.stream.write("second".toByteArray())
        }

    }

    private companion object {

        val Large = ByteArray(4 * 1024 * 1024) { (it % 251).toByte() }

    }

    // endregion

}
