package org.genhttp.testing.acceptance.modules.websockets.integration.reactive

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.modules.conversion.serializers.json.JsonFormat
import org.genhttp.modules.websockets.ReactiveConnection
import org.genhttp.modules.websockets.Websocket
import org.genhttp.modules.websockets.WebsocketFrame
import org.genhttp.modules.websockets.protocol.FrameError
import org.genhttp.modules.websockets.readPayload
import org.genhttp.modules.websockets.writePayload
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.modules.websockets.integration.Client
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.Chain

import org.genhttp.modules.websockets.ReactiveHandler as IReactiveHandler

class IntegrationTests {

    @MultiEngineTest
    fun testServerReactive(engine: ServerEngine) = test {
        val websocket = Websocket.reactive()
            .handleContinuationFramesManually()
            .handler(ReactiveHandler())

        Chain.works(websocket)

        TestHost.run(websocket, engine = engine).use { host ->
            Client.execute(host.port)
        }
    }

    @MultiEngineTest
    fun testSerialization(engine: ServerEngine) = test {
        val websocket = Websocket.reactive()
            .serialization(JsonFormat())
            .handler(ReactiveHandlerSerialized())

        TestHost.run(websocket, engine = engine).use { host ->
            Client.executeSerialized(host.port)
        }
    }

    // Automatic segmented handling
    @MultiEngineTest
    fun testServerReactiveSegmented(engine: ServerEngine) = test {
        val websocket = Websocket.reactive()
            .handler(ReactiveHandler())

        Chain.works(websocket)

        TestHost.run(websocket, engine = engine).use { host ->
            Client.executeSegmented(host.port)
        }
    }

    // Automatic segmented handling
    // Plus TCP fragmentation
    @MultiEngineTest
    fun testServerReactiveFragmented(engine: ServerEngine) = test {
        val websocket = Websocket.reactive()
            .handler(ReactiveHandlerFragmented())

        Chain.works(websocket)

        TestHost.run(websocket, engine = engine).use { host ->
            Client.executeFragmented("127.0.0.1", host.port)
        }
    }

    // Automatic segmented handling
    // Plus TCP fragmentation
    // Plus segmented message
    @MultiEngineTest
    fun testServerReactiveFragmentedSegmented(engine: ServerEngine) = test {
        val websocket = Websocket.reactive()
            .handler(ReactiveHandlerFragmented())

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
    fun testServerReactiveFragmentedSegmentedNoAllocations(engine: ServerEngine) = test {
        val websocket = Websocket.reactive()
            .doNotAllocateFrameData()
            .handler(ReactiveHandlerFragmented())

        Chain.works(websocket)

        TestHost.run(websocket, engine = engine).use { host ->
            Client.executeFragmentedWithContinuationFrames("127.0.0.1", host.port)
        }
    }

    class ReactiveHandler : IReactiveHandler {

        override suspend fun onConnected(connection: ReactiveConnection) = connection.ping()

        override suspend fun onMessage(connection: ReactiveConnection, message: WebsocketFrame) = connection.write(message.data)

        override suspend fun onContinue(connection: ReactiveConnection, message: WebsocketFrame) = connection.write(message.data)

        override suspend fun onPing(connection: ReactiveConnection, message: WebsocketFrame) = connection.pong(message.data)

        override suspend fun onClose(connection: ReactiveConnection, message: WebsocketFrame) = connection.close()

        override suspend fun onError(connection: ReactiveConnection, error: FrameError): Boolean {
            println("${error.errorType}: ${error.message}")
            return false
        }

    }

    class ReactiveHandlerFragmented : IReactiveHandler {

        override suspend fun onConnected(connection: ReactiveConnection) {}

        override suspend fun onMessage(connection: ReactiveConnection, message: WebsocketFrame) = connection.write(message.data)

        override suspend fun onContinue(connection: ReactiveConnection, message: WebsocketFrame) = connection.write(message.data)

        override suspend fun onPing(connection: ReactiveConnection, message: WebsocketFrame) = connection.pong(message.data)

        override suspend fun onClose(connection: ReactiveConnection, message: WebsocketFrame) = connection.close()

        override suspend fun onError(connection: ReactiveConnection, error: FrameError): Boolean {
            println("${error.errorType}: ${error.message}")
            return false
        }

    }

    class ReactiveHandlerSerialized : IReactiveHandler {

        override suspend fun onMessage(connection: ReactiveConnection, message: WebsocketFrame) {
            val thing = message.readPayload<Client.SerializedThing>()
            connection.writePayload(thing)
        }

    }

}
