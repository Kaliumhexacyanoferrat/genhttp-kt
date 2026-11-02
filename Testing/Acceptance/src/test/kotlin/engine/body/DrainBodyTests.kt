package org.genhttp.testing.acceptance.engine.body

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.Constants
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.wrap

import java.io.ByteArrayInputStream
import java.net.Socket
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.util.Random
import java.util.concurrent.atomic.AtomicInteger

import kotlin.test.assertEquals

class DrainBodyTests {

    // region Tests

    @MultiEngineTest
    fun testIgnoredContentLength(engine: ServerEngine) = test {
        val handler = CountingHandler()

        TestHost.run(handler.wrap(), engine = engine).use { runner ->
            val client = TestHost.getClient()

            repeat(3) {
                val request = post(runner, "ignored body".toByteArray(), chunked = false)

                val response = runner.getResponse(request, client)

                response.assertStatus(ResponseStatus.Ok)
            }

            assertEquals(3, handler.requestCount)
        }
    }

    @MultiEngineTest
    fun testIgnoredChunked(engine: ServerEngine) = test {
        val handler = CountingHandler()

        TestHost.run(handler.wrap(), engine = engine).use { runner ->
            val client = TestHost.getClient()

            repeat(3) {
                val request = post(runner, "ignored chunked body".toByteArray(), chunked = true)

                val response = runner.getResponse(request, client)

                response.assertStatus(ResponseStatus.Ok)
            }

            assertEquals(3, handler.requestCount)
        }
    }

    @MultiEngineTest
    fun testIgnoredLargeContentLength(engine: ServerEngine) = test {
        val payload = ByteArray(256 * 1024)
        Random(42).nextBytes(payload)

        val handler = CountingHandler()

        TestHost.run(handler.wrap(), engine = engine).use { runner ->
            val client = TestHost.getClient()

            repeat(3) {
                val request = post(runner, payload, chunked = false)

                val response = runner.getResponse(request, client)

                response.assertStatus(ResponseStatus.Ok)
            }

            assertEquals(3, handler.requestCount)
        }
    }

    @MultiEngineTest
    fun testIgnoredLargeChunked(engine: ServerEngine) = test {
        val payload = ByteArray(256 * 1024)
        Random(42).nextBytes(payload)

        val handler = CountingHandler()

        TestHost.run(handler.wrap(), engine = engine).use { runner ->
            val client = TestHost.getClient()

            repeat(3) {
                val request = post(runner, payload, chunked = true)

                val response = runner.getResponse(request, client)

                response.assertStatus(ResponseStatus.Ok)
            }

            assertEquals(3, handler.requestCount)
        }
    }

    @MultiEngineTest
    fun testPartialRead(engine: ServerEngine) = test {
        val handler = PartialReadHandler(bytesToRead = 4)

        TestHost.run(handler.wrap(), engine = engine).use { runner ->
            val client = TestHost.getClient()

            repeat(3) {
                val request = post(runner, "Hello, body!".toByteArray(), chunked = false)

                val response = runner.getResponse(request, client)

                response.assertStatus(ResponseStatus.Ok)
            }

            assertEquals(3, handler.requestCount)
        }
    }

    @MultiEngineTest
    fun testPartialReadChunked(engine: ServerEngine) = test {
        val handler = PartialReadHandler(bytesToRead = 4)

        TestHost.run(handler.wrap(), engine = engine).use { runner ->
            val client = TestHost.getClient()

            repeat(3) {
                val request = post(runner, "Hello, body!".toByteArray(), chunked = true)

                val response = runner.getResponse(request, client)

                response.assertStatus(ResponseStatus.Ok)
            }

            assertEquals(3, handler.requestCount)
        }
    }

    @MultiEngineTest
    fun testPipelinedIgnoredBodies(engine: ServerEngine) = test {
        val handler = CountingHandler()

        TestHost.run(handler.wrap(), engine = engine).use { runner ->
            Socket("127.0.0.1", runner.port).use { client ->
                client.soTimeout = Constants.ClientTimeout

                val stream = client.getOutputStream()

                // all requests arrive at once, so each skipped body is followed by the next request in the same buffer
                val requests = "POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 5\r\n\r\nHello" +
                    "POST / HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nHello\r\n0\r\n\r\n" +
                    "POST / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 5\r\nConnection: close\r\n\r\nHello"

                stream.write(requests.toByteArray(Charsets.US_ASCII))
                stream.flush()

                val responses = String(client.getInputStream().readAllBytes(), Charsets.UTF_8)

                assertEquals(3, responses.split("HTTP/1.1 200").size - 1, responses)
                assertEquals(3, handler.requestCount)
            }
        }
    }

    private fun post(runner: TestHost, payload: ByteArray, chunked: Boolean): HttpRequest.Builder {
        val publisher = if (chunked) BodyPublishers.ofInputStream { ByteArrayInputStream(payload) } else BodyPublishers.ofByteArray(payload)

        return runner.getRequest(method = "POST").method("POST", publisher)
    }

    // endregion

    // region Supporting types

    private class CountingHandler : Handler {

        private val count = AtomicInteger()

        val requestCount: Int
            get() = count.get()

        override suspend fun prepare(server: Server) {}

        override suspend fun handle(request: Request): Response? {
            count.incrementAndGet()

            return request.respond().status(ResponseStatus.Ok).build()
        }

    }

    private class PartialReadHandler(private val bytesToRead: Int) : Handler {

        private val count = AtomicInteger()

        val requestCount: Int
            get() = count.get()

        override suspend fun prepare(server: Server) {}

        override suspend fun handle(request: Request): Response? {
            count.incrementAndGet()

            val body = request.getBody()

            if (body != null) {
                body.asStream().readNBytes(bytesToRead)
            }

            return request.respond().status(ResponseStatus.Ok).build()
        }

    }

    // endregion

}
