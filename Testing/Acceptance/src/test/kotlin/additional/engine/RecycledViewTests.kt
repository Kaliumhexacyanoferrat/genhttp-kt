package org.genhttp.testing.acceptance.additional.engine

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.getStringEntry
import org.genhttp.modules.io.content
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.Constants
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.test

import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.Socket
import java.nio.ByteBuffer

import kotlin.test.assertEquals

/**
 * Not ported from GenHTTP: the parser recycles the views on the fields of a request for the next request read from
 * the same connection buffer, so every request must see exactly its own fields, whether the requests arrive one after
 * another (keep-alive) or all at once (pipelining). The requests vary in path, query and number of headers, some of
 * them exceed the number of views kept per connection, one exceeds the read buffer of the connection (which then
 * grows into a new array). A view kept beyond its request (which it must not be) must not fail either.
 */
class RecycledViewTests {

    @MultiEngineTest
    fun testKeepAliveRequestsSeeTheirOwnFields(engine: ServerEngine) = test {
        TestHost.run(EchoHandler(), engine = engine).use { runner ->
            Socket("127.0.0.1", runner.port).use { client ->
                client.soTimeout = Constants.ClientTimeout

                val output = client.getOutputStream()
                val input = BufferedInputStream(client.getInputStream())

                for (request in Requests) {
                    output.write(request.raw.toByteArray(Charsets.US_ASCII))
                    output.flush()

                    assertEquals(request.expected, readBody(input))
                }
            }
        }
    }

    @MultiEngineTest
    fun testPipelinedRequestsSeeTheirOwnFields(engine: ServerEngine) = test {
        TestHost.run(EchoHandler(), engine = engine).use { runner ->
            Socket("127.0.0.1", runner.port).use { client ->
                client.soTimeout = Constants.ClientTimeout

                val output = client.getOutputStream()
                val input = BufferedInputStream(client.getInputStream())

                output.write(Requests.joinToString("") { it.raw }.toByteArray(Charsets.US_ASCII))
                output.flush()

                for (request in Requests) {
                    assertEquals(request.expected, readBody(input))
                }
            }
        }
    }

    // region Supporting types

    private class TestRequest(index: Int, headerCount: Int, queryCount: Int, largeHeaders: Int = 0) {

        // the length goes up and down, so that the view on the path is re-pointed to shorter paths as well
        private val path = "/items/$index/" + "p".repeat((index * 5) % 11)

        private val query = (0 until queryCount).map { "q$it" to "v$index-" + "x".repeat(it + index) }

        private val headers = listOf("Host" to "localhost", "X-Index" to "$index") +
            (0 until headerCount).map { "X-Header-$it" to "h$index-" + "y".repeat((it * 7 + index) % 23) } +
            (0 until largeHeaders).map { "X-Large-$it" to "l$index-" + "z".repeat(7000) }

        val raw: String = buildString {
            append("GET ").append(path)

            if (query.isNotEmpty()) {
                append('?').append(query.joinToString("&") { (key, value) -> "$key=$value" })
            }

            append(" HTTP/1.1\r\n")

            for ((key, value) in headers) {
                append(key).append(": ").append(value).append("\r\n")
            }

            append("\r\n")
        }

        val expected: String = render("GET", path, query, headers)

    }

    private class EchoHandler : Handler {

        // kept beyond its request on purpose, its content is undefined after that, but reading it must not fail
        private var previousPath: ByteString? = null

        override suspend fun prepare(server: Server) {
        }

        override suspend fun handle(request: Request): Response {
            val header = request.header

            previousPath?.let { previous ->
                previous.toString()
                previous.hashCode()
                previous.contentEquals(header.path.bytes)
                previous.bytes

                if (previous == header.path) {
                    previous.copyTo(ByteBuffer.allocate(previous.length))
                }
            }

            previousPath = header.path

            val query = (0 until header.query.count).map { header.query.getStringEntry(it).let { (key, value) -> key.toString() to value.toString() } }
            val headers = (0 until header.headers.count).map { header.headers.getStringEntry(it).let { (key, value) -> key.toString() to value.toString() } }

            val text = render(header.method.toString(), header.path.toString(), query, headers)

            return request.respond()
                .content(text.toByteArray(Charsets.US_ASCII), ContentType.TextPlain)
                .build()
        }

    }

    private companion object {

        // small and large requests alternate, so that views are re-pointed to fields of different sizes and the
        // pool of views is exhausted in between (more than 64 fields)
        val Requests = listOf(
            TestRequest(0, headerCount = 0, queryCount = 0),
            TestRequest(1, headerCount = 5, queryCount = 2),
            TestRequest(2, headerCount = 40, queryCount = 10),
            TestRequest(3, headerCount = 1, queryCount = 1),
            TestRequest(4, headerCount = 20, queryCount = 0),
            TestRequest(5, headerCount = 0, queryCount = 30),
            TestRequest(6, headerCount = 2, queryCount = 1, largeHeaders = 3),
            TestRequest(7, headerCount = 3, queryCount = 3),
            TestRequest(8, headerCount = 40, queryCount = 10)
        )

        fun render(method: String, path: String, query: List<Pair<String, String>>, headers: List<Pair<String, String>>): String = buildString {
            append(method).append(' ').append(path).append('\n')

            for ((key, value) in query) {
                append("query ").append(key).append('=').append(value).append('\n')
            }

            for ((key, value) in headers) {
                append("header ").append(key).append('=').append(value).append('\n')
            }
        }

        /**
         * Reads a response with a content length from the stream and returns its body.
         */
        fun readBody(input: InputStream): String {
            var length = -1

            while (true) {
                val line = readLine(input)

                if (line.isEmpty()) {
                    break
                }

                if (line.startsWith("Content-Length:", ignoreCase = true)) {
                    length = line.substringAfter(':').trim().toInt()
                }
            }

            check(length >= 0) { "Response without content length" }

            return String(input.readNBytes(length), Charsets.US_ASCII)
        }

        fun readLine(input: InputStream): String = buildString {
            while (true) {
                val b = input.read()

                if (b < 0) {
                    throw IOException("Connection closed while reading a response")
                }

                if (b == '\n'.code) {
                    break
                }

                if (b != '\r'.code) {
                    append(b.toChar())
                }
            }
        }

    }

    // endregion

}
