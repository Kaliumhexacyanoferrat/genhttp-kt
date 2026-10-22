package org.genhttp.testing.acceptance.modules.websockets.integration.imperative

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.modules.websockets.ImperativeConnection
import org.genhttp.modules.websockets.ImperativeHandler
import org.genhttp.modules.websockets.Websocket
import org.genhttp.modules.websockets.protocol.FrameType
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.modules.websockets.integration.Client
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.Chain

import org.junit.jupiter.api.Test

class IntegrationTests {

    @MultiEngineTest
    fun testServerImperative(engine: ServerEngine) = test {
        val websocket = Websocket
            .imperative()
            .handleContinuationFramesManually()
            .handler(MyHandler())

        Chain.works(websocket)

        TestHost.run(websocket, engine = engine).use { host ->
            Client.execute(host.port)
        }
    }

    // Automatic segmented handling
    @MultiEngineTest
    fun testServerImperativeSegmented(engine: ServerEngine) = test {
        val websocket = Websocket
            .imperative()
            .handler(MyHandler())

        Chain.works(websocket)

        TestHost.run(websocket, engine = engine).use { host ->
            Client.executeSegmented(host.port)
        }
    }

    // Automatic segmented handling
    // Plus TCP fragmentation
    @Test
    fun testServerImperativeFragmented() = test {
        val websocket = Websocket
            .imperative()
            .handler(MyHandlerFragmented())

        Chain.works(websocket)

        TestHost.run(websocket).use { host ->
            Client.executeFragmented("127.0.0.1", host.port)
        }
    }

    // Automatic segmented handling
    // Plus TCP fragmentation
    // Plus segmented message
    @Test
    fun testServerImperativeFragmentedSegmented() = test {
        val websocket = Websocket
            .imperative()
            .handler(MyHandlerFragmented())

        Chain.works(websocket)

        TestHost.run(websocket).use { host ->
            Client.executeFragmentedWithContinuationFrames("127.0.0.1", host.port)
        }
    }

    // Automatic segmented handling
    // Plus TCP fragmentation
    // Plus segmented message
    // No allocations
    @Test
    fun testServerImperativeFragmentedSegmentedNoAllocations() = test {
        val websocket = Websocket
            .imperative()
            .handler(MyHandlerFragmented())

        Chain.works(websocket)

        TestHost.run(websocket).use { host ->
            Client.executeFragmentedWithContinuationFrames("127.0.0.1", host.port)
        }
    }

    class MyHandler : ImperativeHandler {

        override suspend fun handle(connection: ImperativeConnection) {
            try {
                connection.ping()

                while (connection.request.server.running) {
                    val frame = connection.readFrame()

                    val error = frame.isError()

                    if (error != null) {
                        println("${error.errorType}: ${error.message}")
                        continue
                    }

                    if (frame.type == FrameType.Pong) {
                        continue
                    }

                    if (frame.type == FrameType.Close) {
                        connection.close()
                        break
                    }

                    connection.write(frame.data, FrameType.Text, fin = true)
                }
            } catch (e: Exception) {
                println(e)
                throw e
            }
        }

    }

    class MyHandlerFragmented : ImperativeHandler {

        override suspend fun handle(connection: ImperativeConnection) {
            try {
                while (connection.request.server.running) {
                    val frame = connection.readFrame()

                    val error = frame.isError()

                    if (error != null) {
                        println("${error.errorType}: ${error.message}")
                        continue
                    }

                    if (frame.type == FrameType.Pong) {
                        continue
                    }

                    if (frame.type == FrameType.Close) {
                        connection.close()
                        break
                    }

                    connection.write(frame.data, FrameType.Text, fin = true)
                }
            } catch (e: Exception) {
                println(e)
                throw e
            }
        }

    }

}
