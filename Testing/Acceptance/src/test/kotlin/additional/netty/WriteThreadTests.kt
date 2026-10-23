package org.genhttp.testing.acceptance.additional.netty

import io.netty.channel.EventLoop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseSink
import org.genhttp.engine.netty.EngineOptions
import org.genhttp.engine.netty.EventLoopOptions
import org.genhttp.engine.netty.Transport
import org.genhttp.modules.websockets.ReactiveConnection
import org.genhttp.modules.websockets.ReactiveHandler
import org.genhttp.modules.websockets.Websocket
import org.genhttp.modules.websockets.WebsocketFrame
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.AsyncFunctionalHandler

import org.junit.jupiter.api.Test

import java.io.BufferedInputStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Not ported from GenHTTP: on the Netty engine, a response can be written from other threads than the event loop of
 * its connection: by content that continues on another dispatcher, or by a websocket handler that writes to the
 * connections of other event loops (a broadcast). The data arrives in the order it has been written, and writes of
 * another event loop never wait for the client, as this would halt the connections of that loop.
 */
class WriteThreadTests {

    @Test
    fun testContentWrittenFromOtherDispatcher() = test {
        // including an empty part written after a flush
        val parts = listOf(part('a', 40_000), part('b', 0), part('c', 40_000), part('d', 40_000), part('e', 40_000), part('f', 40_000))

        val expected = parts.reduce(ByteArray::plus)

        val handler = AsyncFunctionalHandler { request -> request.respond().content(AlternatingContent(parts)).build() }

        NettyRunner.run(handler, EngineOptions(eventLoop = EventLoopOptions(count = 1))).use { runner ->
            RawClient.connect(runner.port).use { client ->
                val input = BufferedInputStream(client.getInputStream())

                // one after another
                repeat(3) {
                    RawClient.send(client, RawClient.request())

                    assertTrue(expected.contentEquals(RawClient.readResponse(input).body))
                }

                // pipelined, the last one closing the connection
                RawClient.send(client, RawClient.request() + RawClient.request() + RawClient.request(headers = listOf("Connection" to "close")))

                repeat(3) {
                    assertTrue(expected.contentEquals(RawClient.readResponse(input).body))
                }

                assertEquals(-1, input.read())
            }
        }
    }

    @Test
    fun testWritesOfAnotherLoopStayInOrder() = test {
        val loops = CopyOnWriteArrayList<EventLoop>()

        // larger than the socket buffers, so that the writes of the other loop are still pending when the loop of
        // the connection writes the next part
        val parts = listOf(part('a', 4 * 1024 * 1024), part('b', 64 * 1024), part('c', 4 * 1024 * 1024), part('d', 64 * 1024))

        val expected = parts.reduce(ByteArray::plus)

        val handler = AsyncFunctionalHandler { request -> request.respond().content(LoopHoppingContent(parts, loops)).build() }

        NettyRunner.run(handler, EngineOptions(eventLoop = EventLoopOptions(count = 2)), onEventLoopStart = { loops.add(it) }).use { runner ->
            RawClient.connect(runner.port, receiveBuffer = 8192).use { client ->
                val input = BufferedInputStream(client.getInputStream())

                repeat(2) {
                    RawClient.send(client, RawClient.request())

                    assertTrue(expected.contentEquals(RawClient.readResponse(input).body))
                }
            }
        }
    }

    @Test
    fun testBroadcastToOtherEventLoop() = test {
        val handler = BroadcastHandler()

        // NIO hands the connections to the loops one after another, so the two clients are served by different loops
        NettyRunner.run(Websocket.reactive().handler(handler), BroadcastOptions).use { runner ->
            RawClient.connect(runner.port).use { first ->
                RawClient.connect(runner.port).use { second ->
                    RawClient.upgradeToWebsocket(first)
                    RawClient.upgradeToWebsocket(second)

                    handler.awaitConnections(2)

                    assertEquals(2, handler.loops.size, "The connections are served by the same loop")

                    val clients = listOf(first, second)

                    for (round in 0 until 50) {
                        for ((index, sender) in clients.withIndex()) {
                            val message = "Message $round from $index"

                            RawClient.send(sender, RawClient.textFrame(message))

                            for (receiver in clients) {
                                assertEquals(message, RawClient.readTextFrame(receiver.getInputStream()))
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testBroadcastDoesNotWaitForClientsNotReading() = test {
        val handler = BroadcastHandler()

        NettyRunner.run(Websocket.reactive().handler(handler), BroadcastOptions).use { runner ->
            RawClient.connect(runner.port).use { sender ->
                RawClient.connect(runner.port, receiveBuffer = 8192).use { idle ->
                    RawClient.upgradeToWebsocket(sender)
                    RawClient.upgradeToWebsocket(idle)

                    handler.awaitConnections(2)

                    assertEquals(2, handler.loops.size, "The connections are served by the same loop")

                    // far more than the socket buffers of the client that does not read can hold
                    val count = 2000

                    val input = BufferedInputStream(sender.getInputStream())

                    for (index in 0 until count) {
                        RawClient.send(sender, RawClient.textFrame(message(index)))

                        assertEquals(message(index), RawClient.readTextFrame(input))
                    }

                    // the client that did not read receives everything once it does
                    val idleInput = BufferedInputStream(idle.getInputStream())

                    for (index in 0 until count) {
                        assertEquals(message(index), RawClient.readTextFrame(idleInput))
                    }
                }
            }
        }
    }

    // region Helpers

    /**
     * Writes the given parts, every other one from another dispatcher (with and without flushing).
     */
    private class AlternatingContent(private val parts: List<ByteArray>) : ResponseContent {

        override val length: Long = parts.sumOf { it.size }.toLong()

        override val type: ContentType = ContentType.ApplicationOctetStream

        override val encoding: ByteBuffer? = null

        override suspend fun calculateChecksum(): Long? = null

        override suspend fun write(sink: ResponseSink) {
            for ((index, part) in parts.withIndex()) {
                if (index % 2 == 0) {
                    withContext(Dispatchers.Default) {
                        sink.stream.write(part)

                        if (index % 4 == 0) {
                            sink.stream.flush()
                        }
                    }
                } else {
                    sink.stream.write(part)
                }
            }
        }

    }

    /**
     * Writes every other part from the event loop that does not serve the connection.
     */
    private class LoopHoppingContent(private val parts: List<ByteArray>, private val loops: List<EventLoop>) : ResponseContent {

        override val length: Long = parts.sumOf { it.size }.toLong()

        override val type: ContentType = ContentType.ApplicationOctetStream

        override val encoding: ByteBuffer? = null

        override suspend fun calculateChecksum(): Long? = null

        override suspend fun write(sink: ResponseSink) {
            val other = loops.first { !it.inEventLoop() }.asCoroutineDispatcher()

            for ((index, part) in parts.withIndex()) {
                if (index % 2 == 0) {
                    withContext(other) {
                        sink.stream.write(part)
                        sink.stream.flush()
                    }
                } else {
                    sink.stream.write(part)
                }
            }
        }

    }

    /**
     * Sends every message received to all connected clients.
     */
    private class BroadcastHandler : ReactiveHandler {

        private val connections = CopyOnWriteArrayList<ReactiveConnection>()

        /**
         * The threads the connections are served on.
         */
        val loops: MutableSet<Thread> = ConcurrentHashMap.newKeySet()

        override suspend fun onConnected(connection: ReactiveConnection) {
            loops.add(Thread.currentThread())
            connections.add(connection)
        }

        override suspend fun onMessage(connection: ReactiveConnection, message: WebsocketFrame) {
            for (receiver in connections) {
                receiver.write(message.data.duplicate())
            }
        }

        override suspend fun onClose(connection: ReactiveConnection, message: WebsocketFrame) {
            connections.remove(connection)
            connection.close()
        }

        fun awaitConnections(count: Int) {
            val start = TimeSource.Monotonic.markNow()

            while (connections.size < count) {
                assertTrue(start.elapsedNow() < 10.seconds, "Only ${connections.size} of $count clients connected")
                Thread.sleep(5)
            }
        }

    }

    private companion object {

        val BroadcastOptions = EngineOptions(eventLoop = EventLoopOptions(count = 2, transport = Transport.Nio))

        fun message(index: Int) = "$index:".padEnd(8000, '.')

        fun part(fill: Char, size: Int) = ByteArray(size) { fill.code.toByte() }

    }

    // endregion

}
