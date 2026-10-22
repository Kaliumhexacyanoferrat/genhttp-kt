package org.genhttp.testing.acceptance.modules.websockets

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseSink
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.io.fromString
import org.genhttp.modules.io.streaming.ResourceContent
import org.genhttp.modules.websockets.provider.WebsocketHandler
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test

import java.net.Socket
import java.nio.ByteBuffer

import kotlin.test.assertEquals

class ProviderTests {

    @MultiEngineTest
    fun testHandshake(engine: ServerEngine) = test {
        val handler = WebsocketHandler { FlushingContent() }

        TestHost.run(handler, engine = engine).use { runner ->
            // the JDK client refuses 101 responses to requests that did not come from its websocket builder,
            // so the request is sent via a plain socket
            val response = sendRaw(
                runner.port,
                "GET / HTTP/1.1\r\n" +
                        "Host: localhost:${runner.port}\r\n" +
                        "Upgrade: websocket\r\n" +
                        "Connection: upgrade\r\n" +
                        "Sec-WebSocket-Key: x3JJHMbDL1EzLkh9GBhXDw==\r\n" +
                        "Sec-WebSocket-Version: 13\r\n" +
                        "\r\n"
            )

            assertEquals(101, response.status)

            assertEquals("websocket", response.headers["upgrade"])
            assertEquals("HSmrc0sMlYUkAGmm5OPpG2HaGWk=", response.headers["sec-websocket-accept"])
        }
    }

    @MultiEngineTest
    fun testBadHandshake(engine: ServerEngine) = test {
        val content = ResourceContent(Resource.fromString("Hello World").build())

        val handler = WebsocketHandler { content }

        TestHost.run(handler, engine = engine).use { runner ->
            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.BadRequest)
        }
    }

    @MultiEngineTest
    fun testBadMethod(engine: ServerEngine) = test {
        val content = ResourceContent(Resource.fromString("Hello World").build())

        val handler = WebsocketHandler { content }

        TestHost.run(handler, engine = engine).use { runner ->
            val request = runner.getRequest(method = "HEAD")

            val response = runner.getResponse(request)

            response.assertStatus(ResponseStatus.MethodNotAllowed)
        }
    }

    @MultiEngineTest
    fun testBadVersion(engine: ServerEngine) = test {
        val content = ResourceContent(Resource.fromString("Hello World").build())

        val handler = WebsocketHandler { content }

        TestHost.run(handler, engine = engine).use { runner ->
            val request = runner.getRequest()

            request.header("Upgrade", "websocket")
            request.header("Connection", "upgrade")
            request.header("Sec-WebSocket-Key", "x3JJHMbDL1EzLkh9GBhXDw==")
            request.header("Sec-WebSocket-Version", " 15")

            val response = runner.getResponse(request)

            response.assertStatus(ResponseStatus.UpgradeRequired)
        }
    }

    private class RawResponse(val status: Int, val headers: Map<String, String>)

    private fun sendRaw(port: Int, request: String): RawResponse {
        Socket("localhost", port).use { socket ->
            socket.soTimeout = 15000

            socket.getOutputStream().write(request.toByteArray(Charsets.US_ASCII))
            socket.getOutputStream().flush()

            val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)

            val status = reader.readLine().split(' ')[1].toInt()

            val headers = mutableMapOf<String, String>()

            while (true) {
                val line = reader.readLine()

                if (line.isNullOrEmpty()) {
                    break
                }

                val separator = line.indexOf(':')

                headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
            }

            return RawResponse(status, headers)
        }
    }

    private class FlushingContent : ResponseContent {

        override val length: Long? = 11

        override val type: ContentType? = null

        override val encoding: ByteBuffer? = null

        override suspend fun calculateChecksum(): Long? = 42

        override suspend fun write(sink: ResponseSink) {
            sink.stream.write("Hello World".toByteArray(Charsets.UTF_8))

            sink.stream.flush()
        }

    }

}
