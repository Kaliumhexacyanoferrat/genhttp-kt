package org.genhttp.testing.acceptance.additional.netty

import org.genhttp.api.protocol.getEntry
import org.genhttp.engine.netty.EngineOptions
import org.genhttp.engine.netty.EventLoopOptions
import org.genhttp.modules.io.content
import org.genhttp.modules.websockets.ReactiveConnection
import org.genhttp.modules.websockets.ReactiveHandler
import org.genhttp.modules.websockets.Websocket
import org.genhttp.modules.websockets.WebsocketFrame
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.AsyncFunctionalHandler

import org.junit.jupiter.api.Test

import java.io.BufferedInputStream

import kotlin.test.assertEquals

/**
 * Not ported from GenHTTP: on the Netty engine, writes on the event loop never wait (Netty queues them), so a client
 * that sends requests or websocket frames without reading the answers is pushed back by TCP instead: the connection
 * stops reading while its client does not read. Upgraded connections served on a virtual thread are pushed back by
 * their writes, which wait while the client does not read. Once the client reads, all answers arrive complete and
 * in order.
 *
 * The client detects the push back by its writes getting stuck (the socket buffers absorb some megabytes before,
 * depending on the platform).
 */
class BackpressureTests {

    @Test
    fun testPipeliningClientIsPushedBack() = test {
        val handler = AsyncFunctionalHandler { request ->
            val index = request.header.headers.getEntry("X-Index")?.toInt() ?: -1

            request.respond().content(payload(index)).build()
        }

        NettyRunner.run(handler, Options).use { runner ->
            connect(runner.port).use { client ->
                val writer = PushbackWriter(client.getOutputStream(), MaxChunks) { index ->
                    RawClient.request(headers = listOf("X-Index" to "$index", "X-Padding" to Padding))
                }

                writer.awaitPushback()

                val input = BufferedInputStream(client.getInputStream())

                writer.readAnswers { index ->
                    val response = RawClient.readResponse(input)

                    assertEquals(200, response.status)
                    assertEquals(payload(index), response.text)
                }
            }
        }
    }

    @Test
    fun testWebsocketClientIsPushedBack() = test {
        val websocket = Websocket.reactive().handler(EchoHandler())

        NettyRunner.run(websocket, Options).use { runner ->
            connect(runner.port).use { client ->
                RawClient.upgradeToWebsocket(client)

                val writer = PushbackWriter(client.getOutputStream(), MaxChunks) { index -> RawClient.textFrame(payload(index)) }

                writer.awaitPushback()

                val input = BufferedInputStream(client.getInputStream())

                writer.readAnswers { index ->
                    assertEquals(payload(index), RawClient.readTextFrame(input))
                }
            }
        }
    }

    @Test
    fun testUpgradedClientIsPushedBack() = test {
        // reads blocking on a virtual thread
        val handler = UpgradeHandler { request, sink ->
            val input = request.upgrade()

            val buffer = ByteArray(16 * 1024)

            while (true) {
                val read = input.read(buffer)

                if (read < 0) {
                    break
                }

                sink.stream.write(buffer, 0, read)
                sink.stream.flush()
            }
        }

        NettyRunner.run(handler, Options).use { runner ->
            connect(runner.port).use { client ->
                UpgradeHandler.upgrade(client)

                val writer = PushbackWriter(client.getOutputStream(), MaxChunks) { index -> payload(index).toByteArray(Charsets.US_ASCII) }

                writer.awaitPushback()

                val input = BufferedInputStream(client.getInputStream())

                writer.readAnswers { index ->
                    assertEquals(payload(index), String(RawClient.readExactly(input, PayloadSize), Charsets.US_ASCII))
                }
            }
        }
    }

    // region Helpers

    /**
     * Connects a client that absorbs only a few answers it does not read (small receive buffer), but writes
     * without being slowed down by delayed acknowledgements of the server (large send buffer).
     */
    private fun connect(port: Int) = RawClient.connect(port, receiveBuffer = 8 * 1024, sendBuffer = 256 * 1024)

    private class EchoHandler : ReactiveHandler {

        override suspend fun onMessage(connection: ReactiveConnection, message: WebsocketFrame) = connection.write(message.data)

    }

    private companion object {

        val Options = EngineOptions(eventLoop = EventLoopOptions(count = 2))

        /**
         * Requests and answers are of the same size, so that the requests absorbed by the buffers of the server do
         * not cause much more data to be answered.
         */
        const val PayloadSize = 8000

        /**
         * The client gives up after this many chunks (about 256 MB), which is far more than the socket buffers hold
         * on any platform (Linux absorbs some megabytes, Windows considerably more). Only what the buffers absorb
         * is actually sent, the answers to this data are read afterwards.
         */
        const val MaxChunks = 32 * 1024

        val Padding = "p".repeat(PayloadSize)

        fun payload(index: Int) = "$index:".padEnd(PayloadSize, '.')

    }

    // endregion

}
