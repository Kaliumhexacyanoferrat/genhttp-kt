package org.genhttp.testing.acceptance.engine

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.functional.*
import org.genhttp.modules.io.content
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.wrap
import org.genhttp.testing.getContent

import java.io.ByteArrayInputStream
import java.net.http.HttpRequest.BodyPublishers
import java.util.Random
import java.util.concurrent.atomic.AtomicInteger

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class ChunkedContentTest {

    // region Tests

    @MultiEngineTest
    fun testChunkedUpload(engine: ServerEngine) = test {
        val inline = Inline.create()
            .put { model: Model -> model }

        TestHost.run(inline, engine = engine).use { runner ->
            val client = TestHost.getClient()

            // the JSON content has no known length, so it is sent chunked
            val request = runner.getRequest(method = "PUT")
                .method("PUT", BodyPublishers.ofInputStream { ByteArrayInputStream("""{"value":"Hello World"}""".toByteArray(Charsets.UTF_8)) })
                .header("Content-Type", "application/json; charset=utf-8")

            val response = runner.getResponse(request, client)

            response.assertStatus(ResponseStatus.Ok)

            val result = response.getContent()

            AssertX.contains("Hello World", result)
        }
    }

    @MultiEngineTest
    fun testChunkedBodyIsRead(engine: ServerEngine) = test {
        val payload = "Hello, chunked world!"

        TestHost.run(BodyEchoHandler().wrap(), engine = engine).use { runner ->
            val client = TestHost.getClient()

            val request = runner.getRequest(method = "POST")
                .method("POST", BodyPublishers.ofInputStream { ByteArrayInputStream(payload.toByteArray(Charsets.UTF_8)) })

            val response = runner.getResponse(request, client)

            response.assertStatus(ResponseStatus.Ok)

            val received = response.body()

            assertContentEquals(payload.toByteArray(Charsets.UTF_8), received)
        }
    }

    @MultiEngineTest
    fun testLargeChunkedBodyIsRead(engine: ServerEngine) = test {
        val random = Random(42)
        val payload = ByteArray(256 * 1024) // 256 KB across many chunks
        random.nextBytes(payload)

        TestHost.run(BodyEchoHandler().wrap(), engine = engine).use { runner ->
            val client = TestHost.getClient()

            val request = runner.getRequest(method = "POST")
                .method("POST", BodyPublishers.ofInputStream { ByteArrayInputStream(payload) })

            val response = runner.getResponse(request, client)

            response.assertStatus(ResponseStatus.Ok)

            val received = response.body()

            assertContentEquals(payload, received)
        }
    }

    @MultiEngineTest
    fun testChunkedBodyIsDrainedWhenNotRead(engine: ServerEngine) = test {
        // The engine must drain an unread chunked body so the connection can be reused.
        val handler = DrainVerifyHandler()

        TestHost.run(handler.wrap(), engine = engine).use { runner ->
            val client = TestHost.getClient()

            repeat(3) {
                val request = runner.getRequest(method = "POST")
                    .method("POST", BodyPublishers.ofInputStream { ByteArrayInputStream("ignored body".toByteArray(Charsets.UTF_8)) })

                val response = runner.getResponse(request, client)

                response.assertStatus(ResponseStatus.Ok)
            }

            assertEquals(3, handler.requestCount)
        }
    }

    // endregion

    // region Supporting types

    data class Model(val value: String)

    private class BodyEchoHandler : Handler {

        override suspend fun prepare(server: Server) {}

        override suspend fun handle(request: Request): Response? {
            val body = request.getBody()
                ?: return request.respond().status(ResponseStatus.BadRequest).build()

            val bytes = body.asMemory().let { buffer -> ByteArray(buffer.remaining()).also { buffer.get(it) } }

            return request.respond()
                .status(ResponseStatus.Ok)
                .content(bytes, ContentType.ApplicationOctetStream)
                .build()
        }

    }

    private class DrainVerifyHandler : Handler {

        private val count = AtomicInteger()

        val requestCount: Int
            get() = count.get()

        override suspend fun prepare(server: Server) {}

        override suspend fun handle(request: Request): Response? {
            count.incrementAndGet()

            return request.respond().status(ResponseStatus.Ok).build()
        }

    }

    // endregion

}
