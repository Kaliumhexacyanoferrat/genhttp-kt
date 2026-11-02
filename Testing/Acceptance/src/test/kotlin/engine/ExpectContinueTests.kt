package org.genhttp.testing.acceptance.engine

import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.functional.*
import org.genhttp.modules.functional.provider.InlineBuilder
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContent

import java.io.EOFException
import java.io.InputStream
import java.net.Socket
import java.net.http.HttpRequest.BodyPublishers
import java.time.Duration

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * A client that sends `Expect: 100-continue` waits for a 100 (Continue) before it sends the
 * body (RFC 9110, section 10.1.1). The engine answers it as the handler starts reading the body, so
 * a handler that answers without reading never invites the upload.
 */
class ExpectContinueTests {

    @MultiEngineTest
    fun testContinueIsSentAsTheBodyIsRead(engine: ServerEngine) = test {
        TestHost.run(countingApp(), engine = engine).use { host ->
            connect(host).use { client ->
                val stream = client.getOutputStream()

                stream.write("PUT / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 5\r\nExpect: 100-continue\r\n\r\n".toByteArray(Charsets.US_ASCII))

                // the body is held back until the server asks for it
                val interim = readHead(client.getInputStream())

                assertTrue(interim.startsWith("HTTP/1.1 100 Continue"), interim)

                stream.write("hello".toByteArray(Charsets.US_ASCII))

                val final = readHead(client.getInputStream())

                assertTrue(final.startsWith("HTTP/1.1 200"), final)
            }
        }
    }

    @MultiEngineTest
    fun testNoContinueIfTheBodyIsNotRead(engine: ServerEngine) = test {
        val app = Inline.create().put { -> "not interested" }

        TestHost.run(app, engine = engine).use { host ->
            connect(host).use { client ->
                val stream = client.getOutputStream()

                stream.write("PUT / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 5\r\nExpect: 100-continue\r\n\r\n".toByteArray(Charsets.US_ASCII))

                val head = readHead(client.getInputStream())

                assertTrue(head.startsWith("HTTP/1.1 200"), head)
            }
        }
    }

    @MultiEngineTest
    fun testTheConnectionServesOnIfTheBodyIsSentAnyway(engine: ServerEngine) = test {
        // A client that got a final response without a 100 may still send the body,
        // which is then skipped so the connection can serve the next request.
        val app = Inline.create().put { -> "not interested" }

        TestHost.run(app, engine = engine).use { host ->
            connect(host).use { client ->
                val stream = client.getOutputStream()

                stream.write("PUT / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 5\r\nExpect: 100-continue\r\n\r\n".toByteArray(Charsets.US_ASCII))

                val head = readHead(client.getInputStream())

                assertTrue(head.startsWith("HTTP/1.1 200"), head)

                assertEquals("not interested", readBody(client.getInputStream(), contentLength(head)))

                stream.write("helloPUT / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 5\r\n\r\nhello".toByteArray(Charsets.US_ASCII))

                val next = readHead(client.getInputStream())

                assertTrue(next.startsWith("HTTP/1.1 200"), next)
            }
        }
    }

    @MultiEngineTest
    fun testTheConnectionServesOnAfterTheContinue(engine: ServerEngine) = test {
        TestHost.run(countingApp(), engine = engine).use { host ->
            connect(host).use { client ->
                val stream = client.getOutputStream()

                repeat(2) {
                    stream.write("PUT / HTTP/1.1\r\nHost: localhost\r\nContent-Length: 5\r\nExpect: 100-continue\r\n\r\n".toByteArray(Charsets.US_ASCII))

                    val interim = readHead(client.getInputStream())

                    assertTrue(interim.startsWith("HTTP/1.1 100 Continue"), interim)

                    stream.write("hello".toByteArray(Charsets.US_ASCII))

                    val final = readHead(client.getInputStream())

                    assertTrue(final.startsWith("HTTP/1.1 200"), final)

                    // the counted length, as the body of the response
                    assertEquals("5", readBody(client.getInputStream(), 1))
                }
            }
        }
    }

    @MultiEngineTest
    fun testClientUploadsWithoutWaiting(engine: ServerEngine) = test {
        TestHost.run(countingApp(), engine = engine).use { host ->
            // the JDK client sends the body only after it received the 100 (Continue), it has no timeout to give up
            // waiting for it (upstream: Expect100ContinueTimeout of 30 seconds), so the timeout of the request
            // fails the test if the 100 never arrives
            val request = host.getRequest(method = "PUT")
                .expectContinue(true)
                .method("PUT", BodyPublishers.ofByteArray(ByteArray(2 * 1024 * 1024)))
                .timeout(Duration.ofSeconds(10))

            TestHost.getClient().use { client ->
                val response = host.getResponse(request, client)

                response.assertStatus(ResponseStatus.Ok)

                assertEquals((2 * 1024 * 1024).toString(), response.getContent())
            }
        }
    }

    @MultiEngineTest
    fun testUnknownExpectationFails(engine: ServerEngine) = test {
        TestHost.run(countingApp(), engine = engine).use { host ->
            val request = host.getRequest(method = "PUT")
                .header("Expect", "something-else")
                .method("PUT", BodyPublishers.ofString("hello"))
                .header("Content-Type", "text/plain; charset=utf-8")

            val response = host.getResponse(request)

            response.assertStatus(ResponseStatus.ExpectationFailed)
        }
    }

    @MultiEngineTest
    fun testHttp10IgnoresTheExpectation(engine: ServerEngine) = test {
        // An HTTP/1.0 client cannot ask for an interim response, so its body follows right away
        // and no 100 may precede the final response.
        TestHost.run(countingApp(), engine = engine).use { host ->
            connect(host).use { client ->
                val stream = client.getOutputStream()

                stream.write("PUT / HTTP/1.0\r\nHost: localhost\r\nContent-Length: 5\r\nExpect: 100-continue\r\n\r\nhello".toByteArray(Charsets.US_ASCII))

                val head = readHead(client.getInputStream())

                assertTrue(head.startsWith("HTTP/1.1 200"), head)
            }
        }
    }

    // region Helpers

    private companion object {

        /**
         * Reads the body to the end and answers with its length.
         */
        fun countingApp(): InlineBuilder = Inline.create().put { body: InputStream ->
            val buffer = ByteArray(64 * 1024)

            var total = 0L

            while (true) {
                val read = body.read(buffer)

                // the end of the stream is signalled by -1 on the JVM (0 upstream)
                if (read <= 0) {
                    break
                }

                total += read
            }

            total
        }

        fun connect(host: TestHost): Socket {
            val client = Socket("127.0.0.1", host.port)

            // the timeout of the reads (upstream cancels a whole ReadHeadAsync/ReadBodyAsync after 5 seconds)
            client.soTimeout = 5000

            return client
        }

        /**
         * One response head, byte by byte so nothing of what follows is consumed with it.
         */
        fun readHead(stream: InputStream): String {
            val head = StringBuilder()

            while (!head.endsWith("\r\n\r\n")) {
                val read = stream.read()

                // the end of the stream is signalled by -1 on the JVM (0 upstream)
                assertNotEquals(-1, read, "The connection closed after: $head")

                head.append(read.toChar())
            }

            return head.toString()
        }

        fun readBody(stream: InputStream, length: Int): String {
            val body = ByteArray(length)

            // ReadExactlyAsync upstream, which fails if the stream ends before
            if (stream.readNBytes(body, 0, length) < length) {
                throw EOFException()
            }

            return String(body, Charsets.US_ASCII)
        }

        fun contentLength(head: String): Int {
            for (line in head.split("\r\n")) {
                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    return line.substring("Content-Length:".length).trim().toInt()
                }
            }

            fail("Response has no Content-Length: $head")
        }

    }

    // endregion

}
