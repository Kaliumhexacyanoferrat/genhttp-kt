package org.genhttp.testing.acceptance.engine.body

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.io.content
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.wrap

import org.junit.jupiter.api.Test

import java.io.ByteArrayInputStream
import java.net.Socket
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.util.Random

import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class MemoryBodyTests {

    // region Tests

    @MultiEngineTest
    fun testSmallContentLength(engine: ServerEngine) = test {
        val payload = "Hello, body!".toByteArray()

        TestHost.run(BodyEchoHandler().wrap(), engine = engine).use { runner ->
            val client = TestHost.getClient()

            val response = runner.getResponse(post(runner, payload, chunked = false), client)

            response.assertStatus(ResponseStatus.Ok)

            assertContentEquals(payload, response.body())
        }
    }

    @MultiEngineTest
    fun testEmptyContentLength(engine: ServerEngine) = test {
        TestHost.run(BodyEchoHandler().wrap(), engine = engine).use { runner ->
            val client = TestHost.getClient()

            // the second request ensures the connection is still usable afterwards
            repeat(2) {
                val response = runner.getResponse(post(runner, ByteArray(0), chunked = false), client)

                response.assertStatus(ResponseStatus.Ok)

                assertTrue(response.body().isEmpty())
            }
        }
    }

    @MultiEngineTest
    fun testSmallChunked(engine: ServerEngine) = test {
        val payload = "Hello, chunked body!".toByteArray()

        TestHost.run(BodyEchoHandler().wrap(), engine = engine).use { runner ->
            val client = TestHost.getClient()

            val response = runner.getResponse(post(runner, payload, chunked = true), client)

            response.assertStatus(ResponseStatus.Ok)

            assertContentEquals(payload, response.body())
        }
    }

    @MultiEngineTest
    fun testLargeContentLength(engine: ServerEngine) = test {
        val payload = ByteArray(256 * 1024)
        Random(42).nextBytes(payload)

        TestHost.run(BodyEchoHandler().wrap(), engine = engine).use { runner ->
            val client = TestHost.getClient()

            val response = runner.getResponse(post(runner, payload, chunked = false), client)

            response.assertStatus(ResponseStatus.Ok)

            assertContentEquals(payload, response.body())
        }
    }

    @MultiEngineTest
    fun testLargeChunked(engine: ServerEngine) = test {
        val payload = ByteArray(256 * 1024)
        Random(42).nextBytes(payload)

        TestHost.run(BodyEchoHandler().wrap(), engine = engine).use { runner ->
            val client = TestHost.getClient()

            val response = runner.getResponse(post(runner, payload, chunked = true), client)

            response.assertStatus(ResponseStatus.Ok)

            assertContentEquals(payload, response.body())
        }
    }

    @MultiEngineTest
    fun testKeepAliveContentLength(engine: ServerEngine) = test {
        val first = "first request body".toByteArray()
        val second = "second request body".toByteArray()

        TestHost.run(BodyEchoHandler().wrap(), engine = engine).use { runner ->
            val client = TestHost.getClient()

            val resp1 = runner.getResponse(post(runner, first, chunked = false), client)

            resp1.assertStatus(ResponseStatus.Ok)

            assertContentEquals(first, resp1.body())

            val resp2 = runner.getResponse(post(runner, second, chunked = false), client)

            resp2.assertStatus(ResponseStatus.Ok)

            assertContentEquals(second, resp2.body())
        }
    }

    @MultiEngineTest
    fun testKeepAliveChunked(engine: ServerEngine) = test {
        val first = "first chunked request body".toByteArray()
        val second = "second chunked request body".toByteArray()

        TestHost.run(BodyEchoHandler().wrap(), engine = engine).use { runner ->
            val client = TestHost.getClient()

            val resp1 = runner.getResponse(post(runner, first, chunked = true), client)

            resp1.assertStatus(ResponseStatus.Ok)

            assertContentEquals(first, resp1.body())

            val resp2 = runner.getResponse(post(runner, second, chunked = true), client)

            resp2.assertStatus(ResponseStatus.Ok)

            assertContentEquals(second, resp2.body())
        }
    }

    @Test
    fun testTruncatedContentLengthDoesNotSpin() = test {
        TestHost.run(BodyEchoHandler().wrap()).use { runner ->
            Socket("127.0.0.1", runner.port).use { client ->
                client.soTimeout = 15000

                val request = "POST / HTTP/1.1\r\n" +
                        "Host: localhost\r\n" +
                        "Content-Length: 100\r\n" +
                        "\r\n" +
                        "short" // only 5 of the promised 100 bytes

                val out = client.getOutputStream()

                out.write(request.toByteArray(Charsets.US_ASCII))
                out.flush()

                // signal end of the request body without ever sending the rest
                client.shutdownOutput()

                val response = client.getInputStream().readAllBytes().toString(Charsets.UTF_8)

                // the connection is closed cleanly with an error instead of spinning;
                // an empty response would mean the server never got past the body read
                assertTrue(response.startsWith("HTTP/1.1 400"), response)
            }
        }
    }

    private fun post(runner: TestHost, payload: ByteArray, chunked: Boolean): HttpRequest.Builder {
        val publisher = if (chunked) BodyPublishers.ofInputStream { ByteArrayInputStream(payload) } else BodyPublishers.ofByteArray(payload)

        return runner.getRequest(method = "POST").method("POST", publisher)
    }

    // endregion

    // region Supporting types

    private class BodyEchoHandler : Handler {

        override suspend fun prepare(server: Server) {}

        override suspend fun handle(request: Request): Response? {
            val body = request.getBody()
                ?: return request.respond().status(ResponseStatus.BadRequest).build()

            val buffer = body.asMemory()
            val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }

            return request.respond()
                .status(ResponseStatus.Ok)
                .content(bytes, ContentType.ApplicationOctetStream)
                .build()
        }

    }

    // endregion

}
