package org.genhttp.testing.acceptance.modules.dependencyinjection

import org.genhttp.modules.dependencyinjection.dependentHandler
import org.genhttp.modules.websockets.ReactiveConnection
import org.genhttp.modules.websockets.ReactiveHandler
import org.genhttp.modules.websockets.Websocket
import org.genhttp.testing.acceptance.modules.websockets.integration.TestWebSocket
import org.genhttp.testing.acceptance.test

import org.junit.jupiter.api.Test

import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ReactiveWebsocketTests {

    @Test
    fun testReactiveHandler() = test {
        val websocket = Websocket.reactive()
            .dependentHandler<Handler>()

        DependentHost.run(websocket).use { host ->
            val client = TestWebSocket.connect("ws://localhost:${host.port}")

            assertNotNull(client)

            client.dispose()
        }
    }

    class Handler(private val dependency: AwesomeService) : ReactiveHandler {

        override suspend fun onConnected(connection: ReactiveConnection) {
            assertEquals("42", dependency.doWork())
        }

    }

}
