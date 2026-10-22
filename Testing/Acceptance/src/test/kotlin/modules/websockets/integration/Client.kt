package org.genhttp.testing.acceptance.modules.websockets.integration

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout

import org.genhttp.testing.acceptance.modules.websockets.rawclient.RawWebSocketClient

import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.util.concurrent.CompletionStage

import kotlin.test.assertEquals

/**
 * The kind of message received by a [TestWebSocket] (counterpart of System.Net.WebSockets.WebSocketMessageType).
 */
enum class WebSocketMessageType {
    Text,
    Binary,
    Close
}

/**
 * A message received by a [TestWebSocket].
 */
class ReceivedMessage(val messageType: WebSocketMessageType, val data: ByteArray) {

    val text: String
        get() = String(data, Charsets.UTF_8)

}

/**
 * A websocket client based on the JDK client (counterpart of System.Net.WebSockets.ClientWebSocket).
 */
class TestWebSocket private constructor(private val socket: WebSocket, private val messages: Channel<ReceivedMessage>) {

    /**
     * Sends a text message (or a part of it, if [endOfMessage] is false).
     */
    suspend fun sendText(data: ByteArray, endOfMessage: Boolean) {
        socket.sendText(String(data, Charsets.UTF_8), endOfMessage).await()
    }

    /**
     * Receives the next message sent by the server.
     */
    suspend fun receive(): ReceivedMessage = messages.receive()

    /**
     * Starts the closing handshake and waits for the server to confirm it.
     */
    suspend fun close(status: Int, reason: String) {
        socket.sendClose(status, reason).await()

        while (receive().messageType != WebSocketMessageType.Close) {
            // wait for the close frame of the server
        }
    }

    /**
     * Releases the resources of the connection.
     */
    fun dispose() = socket.abort()

    private class Listener(private val messages: Channel<ReceivedMessage>) : WebSocket.Listener {

        private val text = StringBuilder()

        private val binary = ByteArrayOutputStream()

        override fun onOpen(webSocket: WebSocket) {
            webSocket.request(1)
        }

        override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
            text.append(data)

            if (last) {
                messages.trySend(ReceivedMessage(WebSocketMessageType.Text, text.toString().toByteArray(Charsets.UTF_8)))
                text.setLength(0)
            }

            webSocket.request(1)

            return null
        }

        override fun onBinary(webSocket: WebSocket, data: ByteBuffer, last: Boolean): CompletionStage<*>? {
            val bytes = ByteArray(data.remaining())
            data.get(bytes)

            binary.write(bytes)

            if (last) {
                messages.trySend(ReceivedMessage(WebSocketMessageType.Binary, binary.toByteArray()))
                binary.reset()
            }

            webSocket.request(1)

            return null
        }

        override fun onPing(webSocket: WebSocket, message: ByteBuffer): CompletionStage<*>? {
            // the pong is sent by the client implementation
            webSocket.request(1)

            return null
        }

        override fun onPong(webSocket: WebSocket, message: ByteBuffer): CompletionStage<*>? {
            webSocket.request(1)

            return null
        }

        override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage<*>? {
            messages.trySend(ReceivedMessage(WebSocketMessageType.Close, ByteArray(0)))
            messages.close()

            return null
        }

        override fun onError(webSocket: WebSocket, error: Throwable) {
            messages.close(error)
        }

    }

    companion object {

        private val httpClient: HttpClient by lazy { HttpClient.newHttpClient() }

        /**
         * Connects to the given web socket endpoint.
         */
        suspend fun connect(uri: String, headers: Map<String, String> = emptyMap()): TestWebSocket {
            val messages = Channel<ReceivedMessage>(Channel.UNLIMITED)

            val builder = httpClient.newWebSocketBuilder()

            for ((name, value) in headers) {
                builder.header(name, value)
            }

            val socket = builder.buildAsync(URI.create(uri), Listener(messages)).await()

            return TestWebSocket(socket, messages)
        }

    }

}

object Client {

    suspend fun execute(port: Int) {
        withTimeout(4000) {
            val client = TestWebSocket.connect("ws://localhost:$port/")

            // Sending a Text message
            val bytes = "Hello, World!".toByteArray(Charsets.UTF_8)
            client.sendText(bytes, true)

            var response = client.receive()

            assertEquals(WebSocketMessageType.Text, response.messageType)
            assertEquals("Hello, World!", response.text)

            // Sending a fragmented Text message
            //
            // First frame
            //
            val firstFrame = "This is the first segment".toByteArray(Charsets.UTF_8)

            client.sendText(firstFrame, false)
            response = client.receive()

            assertEquals(WebSocketMessageType.Text, response.messageType)
            assertEquals("This is the first segment", response.text)
            //
            // Second frame
            //
            val secondFrame = "This is the second segment".toByteArray(Charsets.UTF_8)

            client.sendText(secondFrame, true)
            response = client.receive()

            assertEquals(WebSocketMessageType.Text, response.messageType)
            assertEquals("This is the second segment", response.text)

            // Close connection
            client.close(1000, "bye")

            try {
                client.dispose()
            } catch (e: Exception) {
                println(e)
            }
        }
    }

    suspend fun executeSegmented(port: Int) {
        withTimeout(4000) {
            val client = TestWebSocket.connect("ws://localhost:$port/")

            // Sending a Text message
            val bytes = "Hello, World!".toByteArray(Charsets.UTF_8)
            client.sendText(bytes, true)

            var response = client.receive()

            assertEquals(WebSocketMessageType.Text, response.messageType)
            assertEquals("Hello, World!", response.text)

            // Sending a fragmented Text message
            //
            // First frame
            //
            val firstFrame = "First segment - ".toByteArray(Charsets.UTF_8)
            client.sendText(firstFrame, false)
            //
            // Second frame
            //
            val secondFrame = "Second segment".toByteArray(Charsets.UTF_8)

            client.sendText(secondFrame, true)
            response = client.receive()

            assertEquals(WebSocketMessageType.Text, response.messageType)
            assertEquals("First segment - Second segment", response.text)

            // Close connection
            client.close(1000, "bye")

            try {
                client.dispose()
            } catch (e: Exception) {
                println(e)
            }
        }
    }

    fun executeFragmented(host: String, port: Int) {
        RawWebSocketClient(readTimeoutMillis = 5000).use { client ->
            client.connect(host, port)

            // Single WebSocket frame fragmented over TCP writes
            client.sendTextInTcpChunks("Hello over multiple sends!", chunkSize = 3)

            // Multiple frames in a single TCP write
            client.sendMultipleTextFramesSingleWrite(
                "First frame",
                "Second frame",
                "Third frame"
            )

            // Read echoes: we expect 4 frames back
            val echo1 = client.receiveTextFrame()
            val echo2 = client.receiveTextFrame()
            val echo3 = client.receiveTextFrame()
            val echo4 = client.receiveTextFrame()

            assertEquals("Hello over multiple sends!", echo1)
            assertEquals("First frame", echo2)
            assertEquals("Second frame", echo3)
            assertEquals("Third frame", echo4)
        }
    }

    fun executeFragmentedWithContinuationFrames(host: String, port: Int) {
        RawWebSocketClient(readTimeoutMillis = 5000).use { client ->
            client.connect(host, port)

            // One logical message split across WebSocket continuation frames (and TCP-chunked)
            client.sendTextAsContinuationFramesInTcpChunks(
                "Hello split across continuation frames!",
                wsFragmentPayloadSize = 4,
                tcpChunkSize = 3
            )

            // Multiple complete frames in one TCP write
            client.sendMultipleTextFramesSingleWrite(
                "First frame",
                "Second frame",
                "Third frame"
            )

            // Read echoes as MESSAGES (not single frames)
            val echo1 = client.receiveTextFrame()
            val echo2 = client.receiveTextFrame()
            val echo3 = client.receiveTextFrame()
            val echo4 = client.receiveTextFrame()

            assertEquals("Hello split across continuation frames!", echo1)
            assertEquals("First frame", echo2)
            assertEquals("Second frame", echo3)
            assertEquals("Third frame", echo4)
        }
    }

    suspend fun executeSerialized(port: Int) {
        withTimeout(4000) {
            val client = TestWebSocket.connect("ws://localhost:$port/")

            // Sending a serialized message
            val serializedObject = "{\"message\":\"Hello World!\"}"

            val bytes = serializedObject.toByteArray(Charsets.UTF_8)
            client.sendText(bytes, true)

            val response = client.receive()

            assertEquals(WebSocketMessageType.Text, response.messageType)
            assertEquals(serializedObject, response.text)

            // Close connection
            client.close(1000, "bye")

            try {
                client.dispose()
            } catch (e: Exception) {
                println(e)
            }
        }
    }

    class SerializedThing {

        var message: String? = null

    }

}
