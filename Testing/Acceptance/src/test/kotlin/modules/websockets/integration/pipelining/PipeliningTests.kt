package org.genhttp.testing.acceptance.modules.websockets.integration.pipelining

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.modules.websockets.ImperativeConnection
import org.genhttp.modules.websockets.ImperativeHandler
import org.genhttp.modules.websockets.Websocket
import org.genhttp.modules.websockets.protocol.FrameType
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.modules.websockets.rawclient.RawWebSocketClient
import org.genhttp.testing.acceptance.test

import java.nio.ByteBuffer

import kotlin.test.assertEquals

class PipeliningTests {

    /**
     * Three frames written with flush:false followed by an explicit FlushAsync
     * must all arrive at the client in a single batch.
     */
    @MultiEngineTest
    fun testBatchedWritesWithExplicitFlush(engine: ServerEngine) = test {
        val websocket = Websocket
            .imperative()
            .handler(BatchHandler())

        TestHost.run(websocket, engine = engine).use { host ->
            RawWebSocketClient().use { client ->
                client.connect("127.0.0.1", host.port)

                client.sendRawInChunks(RawWebSocketClient.buildClientFrame("go".toByteArray(Charsets.UTF_8), opcode = 0x1, fin = true), chunkSize = 64)

                val r1 = client.receiveTextFrame()
                val r2 = client.receiveTextFrame()
                val r3 = client.receiveTextFrame()

                assertEquals("frame1", r1)
                assertEquals("frame2", r2)
                assertEquals("frame3", r3)
            }
        }
    }

    /**
     * Frames written with flush:false must be flushed when the last write uses flush:true (the default).
     */
    @MultiEngineTest
    fun testBatchedWritesFlushedByLastWrite(engine: ServerEngine) = test {
        val websocket = Websocket
            .imperative()
            .handler(LastWriteFlushHandler())

        TestHost.run(websocket, engine = engine).use { host ->
            RawWebSocketClient().use { client ->
                client.connect("127.0.0.1", host.port)

                client.sendRawInChunks(RawWebSocketClient.buildClientFrame("go".toByteArray(Charsets.UTF_8), opcode = 0x1, fin = true), chunkSize = 64)

                val r1 = client.receiveTextFrame()
                val r2 = client.receiveTextFrame()
                val r3 = client.receiveTextFrame()

                assertEquals("frame1", r1)
                assertEquals("frame2", r2)
                assertEquals("frame3", r3)
            }
        }
    }

    /**
     * A frame written with flush:false must not reach the client until FlushAsync is called.
     * The test verifies this by interleaving a second send/receive round-trip: only after
     * the server calls FlushAsync does the buffered frame become readable.
     */
    @MultiEngineTest
    fun testBufferedFrameNotDeliveredBeforeFlush(engine: ServerEngine) = test {
        val websocket = Websocket
            .imperative()
            .handler(BufferThenFlushHandler())

        TestHost.run(websocket, engine = engine).use { host ->
            RawWebSocketClient().use { client ->
                client.connect("127.0.0.1", host.port)

                // First trigger: server writes "buffered" with flush:false, then reads again before flushing
                val trigger = "step1".toByteArray(Charsets.UTF_8)
                client.sendRawInChunks(RawWebSocketClient.buildClientFrame(trigger, opcode = 0x1, fin = true), chunkSize = 64)

                // Second trigger: server calls FlushAsync and then writes "done" with flush:true
                val trigger2 = "step2".toByteArray(Charsets.UTF_8)
                client.sendRawInChunks(RawWebSocketClient.buildClientFrame(trigger2, opcode = 0x1, fin = true), chunkSize = 64)

                // Both frames must now be readable in order
                val r1 = client.receiveTextFrame()
                val r2 = client.receiveTextFrame()

                assertEquals("buffered", r1)
                assertEquals("done", r2)
            }
        }
    }

    /**
     * Three frames sent in a single TCP write arrive in one pipe read. The handler
     * should drain all three synchronously via TryReadFrame without any async wait,
     * then receive false on the fourth call indicating the buffer is empty.
     */
    @MultiEngineTest
    fun testTryReadFrameDrainsBufferedFrames(engine: ServerEngine) = test {
        val websocket = Websocket
            .imperative()
            .handler(TryReadHandler())

        TestHost.run(websocket, engine = engine).use { host ->
            RawWebSocketClient().use { client ->
                client.connect("127.0.0.1", host.port)

                // Send three frames in one TCP write so all arrive in the same pipe read
                client.sendMultipleTextFramesSingleWrite("alpha", "beta", "gamma")

                // Server echoes each frame back; all three must arrive
                val r1 = client.receiveTextFrame()
                val r2 = client.receiveTextFrame()
                val r3 = client.receiveTextFrame()

                // The fourth receive is the "empty" signal the handler writes after TryReadFrame returned false
                val r4 = client.receiveTextFrame()

                assertEquals("alpha", r1)
                assertEquals("beta", r2)
                assertEquals("gamma", r3)
                assertEquals("empty", r4)
            }
        }
    }

    /**
     * Sends one complete frame followed by only the header+mask of a second frame in
     * a single TCP write. After ReadFrameAsync returns the first frame, TryReadFrame
     * finds the orphaned header bytes, returns false (Incomplete), and signals the client.
     * The client then sends the missing payload, and ReadFrameAsync assembles the full frame.
     */
    @MultiEngineTest
    fun testTryReadFrameReturnsFalseOnPartialFrame(engine: ServerEngine) = test {
        val websocket = Websocket
            .imperative()
            .handler(PartialFrameHandler())

        TestHost.run(websocket, engine = engine).use { host ->
            RawWebSocketClient().use { client ->
                client.connect("127.0.0.1", host.port)

                // Build both frames as full client frames (masked, as required by the WS spec)
                val frame1Bytes = RawWebSocketClient.buildClientFrame("first".toByteArray(Charsets.UTF_8), opcode = 0x1, fin = true)

                val frame2Payload = "second".toByteArray(Charsets.UTF_8)
                val frame2Bytes = RawWebSocketClient.buildClientFrame(frame2Payload, opcode = 0x1, fin = true)

                // For a small payload the client frame layout is:
                //   [b0][b1 (MASK|len)][4-byte mask key][payload...]
                // Sending only the first 6 bytes (header + mask key, no payload) gives a
                // syntactically valid but incomplete frame that Frame.Decode will reject.
                val headerAndMask = 6
                val partial = frame2Bytes.copyOfRange(0, headerAndMask)
                val rest = frame2Bytes.copyOfRange(headerAndMask, frame2Bytes.size)

                // One TCP write: complete frame1 + partial frame2 (header+mask only)
                val combined = frame1Bytes + partial
                client.sendRawInChunks(combined, chunkSize = combined.size)

                // Server echoes "first", then signals "incomplete" after TryReadFrame returns false
                assertEquals("first", client.receiveTextFrame())
                assertEquals("incomplete", client.receiveTextFrame())

                // Now deliver the missing payload - server can complete frame2
                client.sendRawInChunks(rest, chunkSize = rest.size)

                assertEquals("second", client.receiveTextFrame())
            }
        }
    }

    /**
     * A client-sent ping should be answered with a pong that either carries the ping's
     * payload or is empty, using both of the interface's PongAsync overloads.
     */
    @MultiEngineTest
    fun testPongOverloads(engine: ServerEngine) = test {
        val websocket = Websocket
            .imperative()
            .handler(PongHandler())

        TestHost.run(websocket, engine = engine).use { host ->
            RawWebSocketClient().use { client ->
                client.connect("127.0.0.1", host.port)

                client.sendRawInChunks(RawWebSocketClient.buildClientFrame("ping-data".toByteArray(Charsets.UTF_8), opcode = 0x9, fin = true), chunkSize = 64)

                val (opcode1, _, payload1) = client.receiveFrame()
                val (opcode2, _, payload2) = client.receiveFrame()

                assertEquals(0xA.toByte(), opcode1)
                assertEquals("ping-data", String(payload1, Charsets.UTF_8))

                assertEquals(0xA.toByte(), opcode2)
                assertEquals(0, payload2.size)

                assertEquals("done", client.receiveTextFrame())
            }
        }
    }

    // -------------------------------------------------------------------------
    // Handlers
    // -------------------------------------------------------------------------

    private class PongHandler : ImperativeHandler {

        override suspend fun handle(connection: ImperativeConnection) {
            val frame = connection.readFrame()
            if (frame.type == FrameType.Close) return

            connection.pong(frame.data, flush = false)
            connection.pong(flush = false)
            connection.write(utf8("done"))

            connection.close()
        }

    }

    private class BatchHandler : ImperativeHandler {

        override suspend fun handle(connection: ImperativeConnection) {
            val frame = connection.readFrame()

            if (frame.type == FrameType.Close) return

            connection.write(utf8("frame1"), flush = false)
            connection.write(utf8("frame2"), flush = false)
            connection.write(utf8("frame3"), flush = false)
            connection.flush()

            connection.close()
        }

    }

    private class LastWriteFlushHandler : ImperativeHandler {

        override suspend fun handle(connection: ImperativeConnection) {
            val frame = connection.readFrame()

            if (frame.type == FrameType.Close) return

            connection.write(utf8("frame1"), flush = false)
            connection.write(utf8("frame2"), flush = false)
            connection.write(utf8("frame3")) // flush: true by default

            connection.close()
        }

    }

    private class PartialFrameHandler : ImperativeHandler {

        override suspend fun handle(connection: ImperativeConnection) {
            // First ReadFrameAsync buffers everything (complete frame1 + partial frame2 header)
            val frame1 = connection.readFrame()
            if (frame1.type == FrameType.Close) return

            connection.write(frame1.data)

            // TryReadFrame finds the partial frame2 header -> returns false (Incomplete)
            if (connection.tryReadFrame() == null) {
                connection.write(utf8("incomplete"))
            }

            // ReadFrameAsync now waits for the missing payload bytes
            val frame2 = connection.readFrame()
            if (frame2.type == FrameType.Close) return

            connection.write(frame2.data)
            connection.close()
        }

    }

    private class TryReadHandler : ImperativeHandler {

        override suspend fun handle(connection: ImperativeConnection) {
            // Block until at least the first frame arrives (and the rest are buffered)
            val first = connection.readFrame()
            if (first.type == FrameType.Close) return

            connection.write(first.data, flush = false)

            // Drain whatever else is already in the buffer synchronously
            while (true) {
                val buffered = connection.tryReadFrame() ?: break

                if (buffered.type == FrameType.Close) return
                connection.write(buffered.data, flush = false)
            }

            // Signal that the buffer was empty on the last TryReadFrame call
            connection.write(utf8("empty"), flush = false)
            connection.flush()

            connection.close()
        }

    }

    private class BufferThenFlushHandler : ImperativeHandler {

        override suspend fun handle(connection: ImperativeConnection) {
            // step1: buffer a frame without flushing, then wait for another client message
            val frame1 = connection.readFrame()
            if (frame1.type == FrameType.Close) return

            connection.write(utf8("buffered"), flush = false)

            // step2: now flush, then send a second frame with the normal flush
            val frame2 = connection.readFrame()
            if (frame2.type == FrameType.Close) return

            connection.flush()
            connection.write(utf8("done"))

            connection.close()
        }

    }

}

private fun utf8(text: String): ByteBuffer = ByteBuffer.wrap(text.toByteArray(Charsets.UTF_8))
