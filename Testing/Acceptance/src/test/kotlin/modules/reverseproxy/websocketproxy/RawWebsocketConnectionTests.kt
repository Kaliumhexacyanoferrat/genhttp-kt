package org.genhttp.testing.acceptance.modules.reverseproxy.websocketproxy

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay

import org.genhttp.api.content.Handler
import org.genhttp.api.content.ProviderException
import org.genhttp.api.infrastructure.EndPointCollection
import org.genhttp.api.infrastructure.PropertyBag
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.engine.shared.types.HttpRequest
import org.genhttp.modules.reverseproxy.websocket.RawWebsocketConnection
import org.genhttp.testing.acceptance.test

import org.junit.jupiter.api.Test

import org.slf4j.ILoggerFactory

import java.net.InetAddress
import java.net.ServerSocket

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RawWebsocketConnectionTests {

    @Test
    fun testInvalidUrlThrows() {
        assertFailsWith<IllegalArgumentException> { RawWebsocketConnection("not a valid url") }
    }

    @Test
    fun testDefaultPortsResolveForKnownAndUnknownSchemes() {
        RawWebsocketConnection("http://example.com/")
        RawWebsocketConnection("https://example.com/")
        RawWebsocketConnection("ws://example.com/")
        RawWebsocketConnection("wss://example.com/")
        RawWebsocketConnection("ftp://example.com/") // unrecognized scheme - falls back to port 0
    }

    @Test
    fun testUpgradeWithoutInitializeThrows() = test {
        val connection = RawWebsocketConnection("ws://example.com/")

        // pipe is null before initializeStream() runs, so the guard fires before the request is
        // ever touched - passing an uninitialized request is safe here (Kotlin has no null for it).
        assertFailsWith<IllegalStateException> { connection.tryUpgrade(HttpRequest()) }
    }

    @Test
    fun testUpstreamClosesBeforeHandshakeCompletes() = test {
        ServerSocket(0, 50, InetAddress.getLoopbackAddress()).use { listener ->
            val port = listener.localPort

            val acceptTask = async(Dispatchers.IO) {
                listener.accept().use { socket ->
                    // Send a partial, incomplete handshake response, then close without the terminator.
                    socket.getOutputStream().write("HTTP/1.1 101 Switching".toByteArray(Charsets.US_ASCII))
                    socket.getOutputStream().flush()
                    socket.shutdownOutput()

                    // Give the peer time to observe the clean FIN before we tear the socket down.
                    delay(200)
                }
            }

            RawWebsocketConnection("ws://127.0.0.1:$port/").use { connection ->
                connection.initializeStream()

                val request = HttpRequest()
                request.apply(RunningServer)

                val exception = assertFailsWith<ProviderException> { connection.tryUpgrade(request) }

                assertEquals(ResponseStatus.BadGateway, exception.status)
            }

            acceptTask.await()
        }
    }

    @Test
    fun testUpstreamResetsBeforeHandshakeCompletes() = test {
        ServerSocket(0, 50, InetAddress.getLoopbackAddress()).use { listener ->
            val port = listener.localPort

            val acceptTask = async(Dispatchers.IO) {
                listener.accept().use { socket ->
                    // Drain the client's handshake request first, so the connection is fully established
                    // before we reset it. Otherwise the RST races with the client's connect and can
                    // surface there instead of during the handshake exchange.
                    val buffer = ByteArray(1024)
                    socket.getInputStream().read(buffer)

                    // Abortive close: a linger time of 0 makes close() send an RST instead of a FIN,
                    // reproducing the ECONNRESET a peer triggers when it tears the connection down
                    // mid-handshake.
                    socket.setSoLinger(true, 0)
                    socket.close()
                }
            }

            RawWebsocketConnection("ws://127.0.0.1:$port/").use { connection ->
                connection.initializeStream()

                val request = HttpRequest()
                request.apply(RunningServer)

                val exception = assertFailsWith<ProviderException> { connection.tryUpgrade(request) }

                assertEquals(ResponseStatus.BadGateway, exception.status)
            }

            acceptTask.await()
        }
    }

    /**
     * Counterpart of the NSubstitute stub `Substitute.For<IServer>()` with `Running` returning true.
     */
    private object RunningServer : Server {

        override val serverEngine: ServerEngine get() = notNeeded()

        override val version: String get() = notNeeded()

        override val running: Boolean get() = true

        override val development: Boolean get() = notNeeded()

        override val properties: PropertyBag get() = notNeeded()

        override val logging: ILoggerFactory get() = notNeeded()

        override val endPoints: EndPointCollection get() = notNeeded()

        override val handler: Handler get() = notNeeded()

        override suspend fun start() = notNeeded()

        override suspend fun close() = notNeeded()

        private fun notNeeded(): Nothing = throw UnsupportedOperationException("Not needed by the test")

    }

}
