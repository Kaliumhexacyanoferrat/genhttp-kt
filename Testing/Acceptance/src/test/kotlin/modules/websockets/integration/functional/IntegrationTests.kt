package org.genhttp.testing.acceptance.modules.websockets.integration.functional

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.getEntry
import org.genhttp.modules.conversion.Formatting
import org.genhttp.modules.conversion.serializers.json.JsonFormat
import org.genhttp.modules.websockets.Websocket
import org.genhttp.modules.websockets.readPayload
import org.genhttp.modules.websockets.writePayload
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.modules.websockets.integration.Client
import org.genhttp.testing.acceptance.modules.websockets.integration.TestWebSocket
import org.genhttp.testing.acceptance.modules.websockets.integration.WebSocketMessageType
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.Chain

import kotlinx.coroutines.withTimeout

import java.nio.ByteBuffer

import kotlin.test.assertEquals

class IntegrationTests {

    @MultiEngineTest
    fun testServerFunctional(engine: ServerEngine) = test {
        val websocket = Websocket.functional()
            .handleContinuationFramesManually()
            .onConnected { c -> c.ping() }
            .onMessage { c, m -> c.write(m.data) }
            .onContinue { c, m -> c.write(m.data) }
            .onPing { c, m -> c.pong(m.data) }
            .onClose { c, _ -> c.close() }
            .onError { _, _ -> false }

        Chain.works(websocket)

        TestHost.run(websocket, engine = engine).use { host ->
            Client.execute(host.port)
        }
    }

    @MultiEngineTest
    fun testText(engine: ServerEngine) = test {
        val websocket = Websocket.functional()
            .formatters(Formatting.default().build())
            .onMessage { c, m ->
                val data = m.readPayload<String>()
                c.writePayload(data)
            }

        TestHost.run(websocket, engine = engine).use { host ->
            Client.executeSerialized(host.port)
        }
    }

    @MultiEngineTest
    fun testSerialization(engine: ServerEngine) = test {
        val websocket = Websocket.functional()
            .serialization(JsonFormat())
            .onMessage { c, m ->
                val thing = m.readPayload<Client.SerializedThing>()
                c.writePayload(thing)
            }

        TestHost.run(websocket, engine = engine).use { host ->
            Client.executeSerialized(host.port)
        }
    }

    @MultiEngineTest
    fun testRequestHeaderAvailableAfterUpgrade(engine: ServerEngine) = test {
        val websocket = Websocket.functional()
            .onMessage { c, _ ->
                val header = c.request.header

                val name = header.query.getEntry("name")
                val agent = header.headers.getEntry("X-Agent")

                c.write(ByteBuffer.wrap("$name|$agent".toByteArray(Charsets.UTF_8)))
            }

        TestHost.run(websocket, engine = engine).use { host ->
            withTimeout(4000) {
                val client = TestWebSocket.connect("ws://localhost:${host.port}/?name=alice", mapOf("X-Agent" to "tester"))

                try {
                    client.sendText("ping".toByteArray(Charsets.UTF_8), true)

                    val response = client.receive()

                    assertEquals(WebSocketMessageType.Text, response.messageType)
                    assertEquals("alice|tester", response.text)
                } finally {
                    client.dispose()
                }
            }
        }
    }

    // Automatic segmented handling
    @MultiEngineTest
    fun testServerFunctionalSegmented(engine: ServerEngine) = test {
        val websocket = Websocket.functional()
            .onConnected { c -> c.ping() }
            .onMessage { c, m -> c.write(m.data) }
            .onContinue { c, m -> c.write(m.data) }
            .onPing { c, m -> c.pong(m.data) }
            .onClose { c, _ -> c.close() }
            .onError { _, _ -> false }

        Chain.works(websocket)

        TestHost.run(websocket, engine = engine).use { host ->
            Client.executeSegmented(host.port)
        }
    }

    // Automatic segmented handling
    // Plus TCP fragmentation
    @MultiEngineTest
    fun testServerFunctionalFragmented(engine: ServerEngine) = test {
        val websocket = Websocket.functional()
            .onConnected { }
            .onMessage { c, m -> c.write(m.data) }
            .onContinue { c, m -> c.write(m.data) }
            .onPing { c, m -> c.pong(m.data) }
            .onClose { c, _ -> c.close() }
            .onError { _, _ -> false }

        Chain.works(websocket)

        TestHost.run(websocket, engine = engine).use { host ->
            Client.executeFragmented("127.0.0.1", host.port)
        }
    }

    // Automatic segmented handling
    // Plus TCP fragmentation
    // Plus segmented message
    @MultiEngineTest
    fun testServerFunctionalFragmentedSegmented(engine: ServerEngine) = test {
        val websocket = Websocket.functional()
            .onConnected { }
            .onMessage { c, m -> c.write(m.data) }
            .onContinue { c, m -> c.write(m.data) }
            .onPing { c, m -> c.pong(m.data) }
            .onClose { c, _ -> c.close() }
            .onError { _, _ -> false }

        Chain.works(websocket)

        TestHost.run(websocket, engine = engine).use { host ->
            Client.executeFragmentedWithContinuationFrames("127.0.0.1", host.port)
        }
    }

    // Automatic segmented handling
    // Plus TCP fragmentation
    // Plus segmented message
    // No allocations
    @MultiEngineTest
    fun testServerFunctionalFragmentedSegmentedNoAllocations(engine: ServerEngine) = test {
        val websocket = Websocket.functional()
            .onConnected { }
            .onMessage { c, m -> c.write(m.data) }
            .onContinue { c, m -> c.write(m.data) }
            .onPing { c, m -> c.pong(m.data) }
            .onClose { c, _ -> c.close() }
            .onError { _, _ -> false }

        Chain.works(websocket)

        TestHost.run(websocket, engine = engine).use { host ->
            Client.executeFragmentedWithContinuationFrames("127.0.0.1", host.port)
        }
    }

}
