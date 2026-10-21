package org.genhttp.testing.acceptance.engine

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.HeaderAccess
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.modules.io.strings.StringContent
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.wrap
import org.genhttp.testing.getContent

import java.net.http.HttpRequest.BodyPublishers
import java.util.Random

import kotlin.test.assertEquals

class ProtocolTests {

    /**
     * As a client I can stream data to the server.
     */
    @MultiEngineTest
    fun testPost(engine: ServerEngine) = test {
        val recorder = ValueRecorder()

        val str = "From client with ❤"

        TestHost.run(recorder.wrap(), engine = engine).use { runner ->
            val request = runner.getRequest(method = "POST")
                .method("POST", BodyPublishers.ofString(str))
                .header("Content-Type", "text/plain; charset=utf-8")

            runner.getResponse(request)

            assertEquals(str, recorder.value)
        }
    }

    /**
     * As a client I can submit large data.
     */
    @MultiEngineTest
    fun testPutLarge(engine: ServerEngine) = test {
        TestHost.run(ContentLengthResponder().wrap(), engine = engine).use { runner ->
            val content = ByteArray(65536 * 20) // 1.3 MB

            Random().nextBytes(content)

            val request = runner.getRequest(method = "PUT")
                .method("PUT", BodyPublishers.ofByteArray(content))

            val response = runner.getResponse(request)

            val error = response.getContent()

            assertEquals("1310720", error)
        }
    }

    private class ValueRecorder : Handler {

        var value: String? = null
            private set

        override suspend fun prepare(server: Server) {}

        override suspend fun handle(request: Request): Response? {
            val body = request.getBody(HeaderAccess.Retain)

            if (body != null) {
                value = body.asStream().bufferedReader(Charsets.UTF_8).use { it.readText() }
            }

            return request.respond().build()
        }

    }

    private class ContentLengthResponder : Handler {

        override suspend fun prepare(server: Server) {}

        override suspend fun handle(request: Request): Response? {
            var total = 0

            val body = request.getBody(HeaderAccess.Release)

            if (body != null) {
                val stream = body.asStream()

                var read: Int

                val buffer = ByteArray(4096)

                while (stream.read(buffer, 0, buffer.size).also { read = it } > 0) {
                    total += read
                }
            }

            return request.respond()
                .content(StringContent(total.toString()))
                .build()
        }

    }

}
