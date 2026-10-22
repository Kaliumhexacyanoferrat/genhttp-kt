package org.genhttp.testing.acceptance.modules.websockets.integration.imperative

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.modules.websockets.ImperativeConnection
import org.genhttp.modules.websockets.ImperativeHandler
import org.genhttp.modules.websockets.Websocket
import org.genhttp.modules.websockets.protocol.FrameError
import org.genhttp.modules.websockets.protocol.FrameType
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.modules.websockets.rawclient.RawWebSocketClient
import org.genhttp.testing.acceptance.test

import org.junit.jupiter.api.Test

import java.nio.ByteBuffer

import kotlin.test.assertEquals

class FrameEdgeCaseTests {

    @MultiEngineTest
    fun testPingInterruptsSegmentedMessage(engine: ServerEngine) = test {
        val websocket = Websocket.imperative().handler(EchoAllHandler())

        TestHost.run(websocket, engine = engine).use { host ->
            RawWebSocketClient().use { client ->
                client.connect("127.0.0.1", host.port)

                // Text(fin=0) + Ping(fin=1) interleaved + Continuation(fin=1) completing the message
                val first = RawWebSocketClient.buildClientFrame("Hello ".toByteArray(Charsets.UTF_8), opcode = 0x1, fin = false)
                val ping = RawWebSocketClient.buildClientFrame("ping-data".toByteArray(Charsets.UTF_8), opcode = 0x9, fin = true)
                val last = RawWebSocketClient.buildClientFrame("World".toByteArray(Charsets.UTF_8), opcode = 0x0, fin = true)

                val combined = first + ping + last
                client.sendRawInChunks(combined, chunkSize = combined.size)

                val (pingResponseOpcode, _, pingResponsePayload) = client.receiveFrame()

                assertEquals(0xA.toByte(), pingResponseOpcode)
                assertEquals("ping-data", String(pingResponsePayload, Charsets.UTF_8))

                assertEquals("Hello World", client.receiveTextFrame())
            }
        }
    }

    @MultiEngineTest
    fun testCloseInterruptsSegmentedMessage(engine: ServerEngine) = test {
        val websocket = Websocket.imperative().handler(EchoAllHandler())

        TestHost.run(websocket, engine = engine).use { host ->
            RawWebSocketClient().use { client ->
                client.connect("127.0.0.1", host.port)

                // Text(fin=0) followed directly by a Close - the in-progress segmented message is abandoned
                val first = RawWebSocketClient.buildClientFrame("Hello ".toByteArray(Charsets.UTF_8), opcode = 0x1, fin = false)
                val close = RawWebSocketClient.buildClientFrame(byteArrayOf(), opcode = 0x8, fin = true)

                val combined = first + close
                client.sendRawInChunks(combined, chunkSize = combined.size)

                val (opcode, _, _) = client.receiveFrame()

                assertEquals(0x8.toByte(), opcode)
            }
        }
    }

    @Test
    fun testTruncatedFrameAtEofYieldsError() = test {
        // Internal only: Kestrel tears down the connection outright on a half-closed upgrade
        // stream instead of letting the handler write a response - a deeper ASP.NET Core
        // transport behavior, not something specific to this (engine-agnostic) code path.
        val websocket = Websocket.imperative().handler(EchoAllHandler())

        TestHost.run(websocket, engine = ServerEngine.Internal).use { host ->
            RawWebSocketClient().use { client ->
                client.connect("127.0.0.1", host.port)

                val frame = RawWebSocketClient.buildClientFrame("hello".toByteArray(Charsets.UTF_8), opcode = 0x1, fin = true)

                // header + mask only (6 bytes for a small payload) - the payload never arrives
                val partial = frame.copyOfRange(0, 6)

                client.sendRawInChunks(partial, chunkSize = partial.size)

                client.shutdownWrite()

                assertEquals(FrameError.UnexpectedEndOfStream, client.receiveTextFrame())
            }
        }
    }

    private class EchoAllHandler : ImperativeHandler {

        override suspend fun handle(connection: ImperativeConnection) {
            while (true) {
                val frame = connection.readFrame()

                if (frame.type == FrameType.Close) {
                    connection.close()
                    return
                }

                if (frame.type == FrameType.Ping) {
                    connection.pong(frame.data)
                    continue
                }

                val error = frame.isError()

                if (error != null) {
                    connection.write(ByteBuffer.wrap(error.message.toByteArray(Charsets.UTF_8)))
                    connection.close()
                    return
                }

                connection.write(frame.data)
            }
        }

    }

}
