package org.genhttp.testing.acceptance.additional.ioxide

import org.genhttp.glyph11.parser.ParserLimits
import org.genhttp.modules.io.content
import org.genhttp.testing.acceptance.AssertX
import org.genhttp.testing.acceptance.additional.netty.RawClient
import org.genhttp.testing.acceptance.utilities.AsyncFunctionalHandler

import org.junit.jupiter.api.Test

import java.util.concurrent.atomic.AtomicInteger

import kotlin.test.assertEquals

/**
 * Not ported from GenHTTP: the ioxide engine buffers request bodies completely before the handler is invoked (see
 * docs/DEVIATIONS.md), so it refuses bodies that are too large to be buffered (512 MB) as soon as their length is
 * announced. Headers that do not end within the limit of the parser are refused as soon as the limit is exceeded,
 * without waiting for the end of the header.
 */
class RequestLimitTests {

    @Test
    fun testBodyTooLargeToBeBuffered() = ioxideTest {
        val invocations = AtomicInteger()

        val handler = AsyncFunctionalHandler { request ->
            invocations.incrementAndGet()
            request.respond().content("Read").build()
        }

        IoxideRunner.run(handler).use { runner ->
            RawClient.connect(runner.port).use { client ->
                val length = 512L * 1024 * 1024 + 1

                // the body is not sent, the announcement is enough
                RawClient.send(client, RawClient.request("/", "POST", listOf("Content-Length" to length.toString())))

                val input = client.getInputStream()

                val response = RawClient.readResponse(input)

                assertEquals(413, response.status)
                assertEquals("Close", response.head.header("Connection"))
                AssertX.contains("Request body is too large to be buffered.", response.text)

                assertEquals(-1, input.read())
            }

            assertEquals(0, invocations.get())

            // other requests are still served
            RawClient.connect(runner.port).use { client ->
                RawClient.send(client, RawClient.request("/", "POST", ByteArray(1024)))

                assertEquals("Read", RawClient.readResponse(client.getInputStream()).text)
            }
        }
    }

    @Test
    fun testUnterminatedHeaderTooLarge() = ioxideTest {
        val handler = AsyncFunctionalHandler { request -> request.respond().content("Read").build() }

        IoxideRunner.run(handler).use { runner ->
            RawClient.connect(runner.port).use { client ->
                // exactly one byte above the limit, so that the server has read everything when it answers
                val header = StringBuilder("GET / HTTP/1.1\r\nHost: 127.0.0.1\r\n")

                var index = 0

                while (header.length <= MaxHeaderSize) {
                    header.append("X-Padding-").append(index++).append(": ").append("a".repeat(1000)).append("\r\n")
                }

                header.setLength(MaxHeaderSize + 1)

                RawClient.send(client, header.toString().toByteArray(Charsets.US_ASCII))

                val input = client.getInputStream()

                val response = RawClient.readResponse(input)

                assertEquals(431, response.status)
                AssertX.contains("Total header size exceeds limit.", response.text)

                assertEquals(-1, input.read())
            }
        }
    }

    private companion object {

        val MaxHeaderSize = ParserLimits.Default.maxTotalHeaderBytes

    }

}
