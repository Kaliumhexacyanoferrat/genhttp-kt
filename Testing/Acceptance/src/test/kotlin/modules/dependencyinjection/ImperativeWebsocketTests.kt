package org.genhttp.testing.acceptance.modules.dependencyinjection

import org.genhttp.modules.dependencyinjection.dependentHandler
import org.genhttp.modules.websockets.ImperativeConnection
import org.genhttp.modules.websockets.ImperativeHandler
import org.genhttp.modules.websockets.Websocket
import org.genhttp.testing.acceptance.modules.websockets.integration.TestWebSocket
import org.genhttp.testing.acceptance.test

import org.junit.jupiter.api.Test

import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ImperativeWebsocketTests {

    @Test
    fun testImperativeHandler() = test {
        val websocket = Websocket.imperative()
            .dependentHandler<Handler>()

        DependentHost.run(websocket).use { host ->
            val client = TestWebSocket.connect("ws://localhost:${host.port}")

            assertNotNull(client)

            client.dispose()
        }
    }

    class Handler(private val dependency: AwesomeService) : ImperativeHandler {

        override suspend fun handle(connection: ImperativeConnection) {
            assertEquals("42", dependency.doWork())
        }

    }

}
