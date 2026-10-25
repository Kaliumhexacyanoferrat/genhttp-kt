package org.genhttp.testing.acceptance.additional.ioxide

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.CertificateProvider
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseSink
import org.genhttp.engine.ioxide.EngineOptions
import org.genhttp.engine.ioxide.ReactorOptions
import org.genhttp.engine.ioxide.TcpTransportOptions
import org.genhttp.engine.ioxide.TlsProvider
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.content
import org.genhttp.modules.io.fromString
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.additional.netty.RawClient
import org.genhttp.testing.acceptance.utilities.AsyncFunctionalHandler
import org.genhttp.testing.acceptance.utilities.Security

import org.junit.jupiter.api.Test

import java.net.Socket
import java.nio.ByteBuffer

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Not ported from GenHTTP: the timeouts of the ioxide engine (those of ioxide, disabled by default). The idle timeout
 * closes connections that wait for their client, the send timeout closes connections whose client does not read the
 * data sent to it (plain and secure ones).
 *
 * The timeouts are short, but the bounds checked are generous, so that slow machines pass as well (ioxide checks the
 * deadlines every 250 ms).
 */
class TimeoutTests {

    @Test
    fun testIdleConnectionIsClosed() = ioxideTest {
        IoxideRunner.run(Content.from(Resource.fromString("Hello World!")), tcp(TcpTransportOptions(idleTimeoutMs = 1000))).use { runner ->
            RawClient.connect(runner.port).use { client ->
                RawClient.send(client, RawClient.request())

                assertEquals("Hello World!", RawClient.readResponse(client.getInputStream()).text)

                val idle = TimeSource.Monotonic.markNow()

                RawClient.assertClosedByServer(client, "The idle connection has not been closed")

                // by the timeout, not right away
                assertTrue(idle.elapsedNow() >= 500.milliseconds, "The connection has been closed after ${idle.elapsedNow()}")
            }
        }
    }

    @Test
    fun testIdleTimeoutIsDisabledByDefault() = ioxideTest {
        IoxideRunner.run(Content.from(Resource.fromString("Hello World!"))).use { runner ->
            RawClient.connect(runner.port).use { client ->
                val input = client.getInputStream()

                RawClient.send(client, RawClient.request())
                assertEquals("Hello World!", RawClient.readResponse(input).text)

                // longer than the sweep of ioxide needs to close a connection
                Thread.sleep(1500)

                RawClient.send(client, RawClient.request())
                assertEquals("Hello World!", RawClient.readResponse(input).text)
            }
        }
    }

    @Test
    fun testActiveConnectionIsKeptOpen() = ioxideTest {
        IoxideRunner.run(Content.from(Resource.fromString("Hello World!")), tcp(TcpTransportOptions(idleTimeoutMs = 1000))).use { runner ->
            RawClient.connect(runner.port).use { client ->
                val start = TimeSource.Monotonic.markNow()

                // longer than the idle timeout in total, but never idle for that long
                while (start.elapsedNow() < 2.seconds) {
                    RawClient.send(client, RawClient.request())

                    assertEquals("Hello World!", RawClient.readResponse(client.getInputStream()).text)

                    Thread.sleep(100)
                }
            }
        }
    }

    @Test
    fun testClientNotReadingIsClosedAfterSendTimeout() = ioxideTest {
        val content = LargeContent()

        val handler = AsyncFunctionalHandler { request -> request.respond().content(content).build() }

        IoxideRunner.run(handler, tcp(TcpTransportOptions(sendTimeoutMs = 300))).use { runner ->
            RawClient.connect(runner.port, receiveBuffer = 8192).use { client ->
                assertClosedWhileNotReading(client, content)
            }
        }
    }

    @Test
    fun testSecureClientNotReadingIsClosedAfterSendTimeout() = ioxideTest {
        val content = LargeContent()

        val handler = AsyncFunctionalHandler { request -> request.respond().content(content).build() }

        IoxideRunner(handler, tcp(TcpTransportOptions(sendTimeoutMs = 300, tlsProvider = TlsProvider.Jdk))).use { runner ->
            val port = TestHost.nextPort()

            runner.host.bind(null, port, CertificateProvider.from(Security.getCertificate()))

            runner.start()

            RawClient.connectSecure(port, receiveBuffer = 8192).use { client ->
                assertClosedWhileNotReading(client, content)
            }
        }
    }

    @Test
    fun testSendTimeoutKeepsConnectionsWithResponsesSent() = ioxideTest {
        val large = ByteArray(1024 * 1024)

        val handler = AsyncFunctionalHandler { request ->
            if (request.header.path.toString() == "/large") {
                request.respond().content(large, ContentType.ApplicationOctetStream).build()
            } else {
                request.respond().content("Small").build()
            }
        }

        IoxideRunner.run(handler, tcp(TcpTransportOptions(sendTimeoutMs = 1000))).use { runner ->
            RawClient.connect(runner.port).use { client ->
                val input = client.getInputStream()

                RawClient.send(client, RawClient.request("/large"))
                assertEquals(large.size, RawClient.readResponse(input).body.size)

                // the deadline of the sent response must not close the connection
                Thread.sleep(1500)

                RawClient.send(client, RawClient.request("/small"))
                assertEquals("Small", RawClient.readResponse(input).text)
            }
        }
    }

    // region Helpers

    private fun tcp(options: TcpTransportOptions) = EngineOptions(reactor = ReactorOptions(reactorCount = 2), tcp = options)

    /**
     * Requests the content without reading the response and checks that the connection is closed: the client starts
     * reading after the send timeout expired, but only receives what the socket buffers hold.
     */
    private suspend fun assertClosedWhileNotReading(client: Socket, content: LargeContent) {
        RawClient.send(client, RawClient.request())

        // the reactor is busy while it writes the response (a slow machine may need some time to encrypt it), the
        // send timeout fires afterwards
        withTimeout(60.seconds) { content.written.await() }

        Thread.sleep(1000)

        val (received, _) = RawClient.drain(client.getInputStream())

        assertTrue(received < content.length, "The complete response has been received, the connection has not been closed")
    }

    /**
     * Far more data than the socket buffers of any platform hold, signals when it has been passed to the engine.
     */
    private class LargeContent : ResponseContent {

        private val data = ByteArray(64 * 1024 * 1024)

        val written = CompletableDeferred<Unit>()

        override val length: Long = data.size.toLong()

        override val type: ContentType = ContentType.ApplicationOctetStream

        override val encoding: ByteBuffer? = null

        override suspend fun calculateChecksum(): Long? = null

        override suspend fun write(sink: ResponseSink) {
            sink.stream.write(data)
            written.complete(Unit)
        }

    }

    // endregion

}
