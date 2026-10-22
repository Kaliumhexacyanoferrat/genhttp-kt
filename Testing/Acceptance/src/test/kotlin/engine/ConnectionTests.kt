package org.genhttp.testing.acceptance.engine

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.Connection
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseSink
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.functional.*
import org.genhttp.modules.io.fromString
import org.genhttp.modules.io.streaming.ResourceContent
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.MultiEngineTest
import org.genhttp.testing.acceptance.assertStatus
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.getContentHeader
import org.genhttp.testing.getHeader

import java.nio.ByteBuffer

import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConnectionTests {

    @MultiEngineTest
    fun testClose(engine: ServerEngine) = test {
        val handler = Inline.create()
            .get { r: Request ->
                val resource = Resource.fromString("Hello World").build()

                r.respond()
                    .content(ResourceContent(resource))
                    .connection(Connection.Close)
                    .build()
            }

        TestHost.run(handler, engine = engine).use { runner ->
            val response = runner.getResponse()

            response.assertStatus(ResponseStatus.Ok)

            assertEquals("close", response.getHeader("Connection")?.lowercase())
        }
    }

    @MultiEngineTest
    fun testUpgrade(engine: ServerEngine) = test {
        val handler = Inline.create()
            .get { r: Request ->
                r.respond()
                    .content(UpgradedContent())
                    .status(ResponseStatus.SwitchingProtocols)
                    .connection(Connection.Upgrade)
                    .build()
            }

        TestHost.run(handler, engine = engine).use { runner ->
            // the JDK client refuses 101 responses to requests that did not ask for an upgrade, so read the
            // response header from a raw connection (see docs/DEVIATIONS.md)
            val response = rawHeader(runner.port)

            assertEquals(ResponseStatus.SwitchingProtocols.code, response.first)

            // upgrade responses must not have been chunked encoded
            assertNull(response.second["transfer-encoding"])

            // upgrade responses do not have a content length
            assertNull(response.second["content-length"])

            // upgrade responses cannot be compressed
            assertNull(response.second["content-encoding"])

            // upgrade responses cannot have eTags
            assertNull(response.second["etag"])
        }
    }

    private fun rawHeader(port: Int): Pair<Int, Map<String, String>> {
        java.net.Socket("localhost", port).use { socket ->
            socket.soTimeout = 5000

            val request = listOf("GET / HTTP/1.1", "Host: localhost:$port", "", "").joinToString(CRLF)

            socket.getOutputStream().write(request.toByteArray(Charsets.US_ASCII))
            socket.getOutputStream().flush()

            val reader = socket.getInputStream().bufferedReader(Charsets.US_ASCII)

            val status = reader.readLine().split(' ')[1].toInt()

            val headers = mutableMapOf<String, String>()

            while (true) {
                val line = reader.readLine() ?: break

                if (line.isEmpty()) {
                    break
                }

                val separator = line.indexOf(':')
                headers[line.substring(0, separator).trim().lowercase()] = line.substring(separator + 1).trim()
            }

            return status to headers
        }
    }

    // region Supporting data structures

    private class UpgradedContent : ResponseContent {

        override val length: Long? = null

        override val type: ContentType? = null

        override val encoding: ByteBuffer? = null

        override suspend fun calculateChecksum(): Long? = null

        override suspend fun write(sink: ResponseSink) {
            sink.stream.write("Hello World".toByteArray())
            sink.stream.flush()
        }

    }

    // endregion

}

private const val CRLF = "\r\n"
