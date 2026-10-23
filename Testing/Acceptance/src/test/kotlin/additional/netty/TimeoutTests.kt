package org.genhttp.testing.acceptance.additional.netty

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.CertificateProvider
import org.genhttp.api.protocol.ContentType
import org.genhttp.engine.netty.EngineOptions
import org.genhttp.engine.netty.TcpTransportOptions
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.content
import org.genhttp.modules.io.fromString
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.AsyncFunctionalHandler
import org.genhttp.testing.acceptance.utilities.Security
import org.genhttp.testing.getContent

import org.junit.jupiter.api.Test

import java.io.IOException
import java.net.URI
import java.net.http.HttpRequest
import java.net.http.HttpResponse

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Not ported from GenHTTP: the timeouts of the Netty engine (those of ioxide). The idle timeout closes connections
 * that neither receive nor send anything, the send timeout closes connections whose client does not read the data
 * sent to it (plain and secure ones, as well as upgraded connections served on a virtual thread), and the handshake
 * timeout closes TLS connections that do not complete the handshake in time.
 */
class TimeoutTests {

    @Test
    fun testIdleConnectionIsClosed() = test {
        NettyRunner.run(Content.from(Resource.fromString("Hello World!")), tcp(TcpTransportOptions(idleTimeout = 300.milliseconds))).use { runner ->
            RawClient.connect(runner.port).use { client ->
                RawClient.send(client, RawClient.request())

                val input = client.getInputStream()

                assertEquals("Hello World!", RawClient.readResponse(input).text)

                val idle = TimeSource.Monotonic.markNow()

                assertEquals(-1, input.read())

                assertTrue(idle.elapsedNow() >= 200.milliseconds, "The connection has been closed after ${idle.elapsedNow()}")
            }
        }
    }

    @Test
    fun testActiveConnectionIsKeptOpen() = test {
        NettyRunner.run(Content.from(Resource.fromString("Hello World!")), tcp(TcpTransportOptions(idleTimeout = 500.milliseconds))).use { runner ->
            RawClient.connect(runner.port).use { client ->
                val start = TimeSource.Monotonic.markNow()

                // longer than the idle timeout in total, but never idle for that long
                while (start.elapsedNow() < 1.seconds) {
                    RawClient.send(client, RawClient.request())

                    assertEquals("Hello World!", RawClient.readResponse(client.getInputStream()).text)

                    Thread.sleep(100)
                }
            }
        }
    }

    @Test
    fun testClientNotReadingIsClosedAfterSendTimeout() = test {
        val large = ByteArray(16 * 1024 * 1024)

        val handler = AsyncFunctionalHandler { request -> request.respond().content(large, ContentType.ApplicationOctetStream).build() }

        NettyRunner.run(handler, tcp(TcpTransportOptions(sendTimeout = 300.milliseconds))).use { runner ->
            RawClient.connect(runner.port, receiveBuffer = 8192).use { client ->
                RawClient.send(client, RawClient.request())

                // the response cannot be sent while the client does not read
                Thread.sleep(1000)

                val (received, _) = RawClient.drain(client.getInputStream())

                assertTrue(received < large.size, "The complete response has been received, the connection has not been closed")
            }
        }
    }

    @Test
    fun testSecureClientNotReadingIsClosedAfterSendTimeout() = test {
        val large = ByteArray(16 * 1024 * 1024)

        val handler = AsyncFunctionalHandler { request -> request.respond().content(large, ContentType.ApplicationOctetStream).build() }

        NettyRunner(handler, tcp(TcpTransportOptions(sendTimeout = 300.milliseconds))).use { runner ->
            val port = TestHost.nextPort()

            runner.host.bind(null, port, CertificateProvider.from(Security.getCertificate()))

            runner.start()

            RawClient.connectSecure(port, receiveBuffer = 8192).use { client ->
                RawClient.send(client, RawClient.request())

                // the response cannot be sent while the client does not read
                Thread.sleep(1000)

                val (received, _) = RawClient.drain(client.getInputStream())

                assertTrue(received < large.size, "The complete response has been received, the connection has not been closed")
            }
        }
    }

    @Test
    fun testSendTimeoutKeepsConnectionsWithResponsesSent() = test {
        val large = ByteArray(1024 * 1024)

        val handler = AsyncFunctionalHandler { request ->
            if (request.header.path.toString() == "/large") {
                request.respond().content(large, ContentType.ApplicationOctetStream).build()
            } else {
                request.respond().content("Small").build()
            }
        }

        NettyRunner.run(handler, tcp(TcpTransportOptions(sendTimeout = 500.milliseconds))).use { runner ->
            RawClient.connect(runner.port).use { client ->
                val input = client.getInputStream()

                RawClient.send(client, RawClient.request("/large"))
                assertEquals(large.size, RawClient.readResponse(input).body.size)

                // the deadline of the sent response must not close the connection
                Thread.sleep(800)

                RawClient.send(client, RawClient.request("/small"))
                assertEquals("Small", RawClient.readResponse(input).text)
            }
        }
    }

    @Test
    fun testUpgradedClientNotReadingIsClosedAfterSendTimeout() = test {
        val failure = CompletableDeferred<Throwable?>()

        val handler = UpgradeHandler { _, sink ->
            val chunk = ByteArray(16 * 1024)

            try {
                // far more than the socket buffers can hold
                repeat(4096) {
                    sink.stream.write(chunk)
                    sink.stream.flush()
                }

                failure.complete(null)
            } catch (e: Throwable) {
                failure.complete(e)
            }
        }

        NettyRunner.run(handler, tcp(TcpTransportOptions(sendTimeout = 300.milliseconds))).use { runner ->
            RawClient.connect(runner.port, receiveBuffer = 8192).use { client ->
                UpgradeHandler.upgrade(client)

                val error = withTimeout(10.seconds) { failure.await() }

                assertIs<IOException>(error, "The writes of the virtual thread did not fail")

                val (received, _) = RawClient.drain(client.getInputStream())

                assertTrue(received < 4096L * 16 * 1024)
            }
        }
    }

    @Test
    fun testHandshakeTimeout() = test {
        NettyRunner(Content.from(Resource.fromString("Hello World!")), tcp(TcpTransportOptions(handshakeTimeout = 300.milliseconds))).use { runner ->
            val port = TestHost.nextPort()

            runner.host.bind(null, port, CertificateProvider.from(Security.getCertificate()))

            runner.start()

            // a client that does not start the handshake
            RawClient.connect(port).use { client ->
                val connected = TimeSource.Monotonic.markNow()

                val (received, _) = RawClient.drain(client.getInputStream())

                assertEquals(0L, received)
                assertTrue(connected.elapsedNow() >= 200.milliseconds, "The connection has been closed after ${connected.elapsedNow()}")
                assertTrue(connected.elapsedNow() < 10.seconds)
            }

            // clients completing the handshake in time are served
            val request = HttpRequest.newBuilder(URI.create("https://localhost:$port/")).GET().build()

            val response = TestHost.getClient(ignoreSecurityErrors = true).sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).await()

            assertEquals("Hello World!", response.getContent())
        }
    }

    // region Helpers

    private fun tcp(options: TcpTransportOptions) = EngineOptions(tcp = options)

    // endregion

}
