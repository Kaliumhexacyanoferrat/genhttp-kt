package org.genhttp.testing.acceptance.modules.reverseproxy.websocketproxy

import kotlinx.coroutines.withTimeout

import org.genhttp.api.infrastructure.CertificateProvider
import org.genhttp.api.infrastructure.SslProtocol
import org.genhttp.modules.layouting.Layout
import org.genhttp.modules.reverseproxy.Proxy
import org.genhttp.modules.websockets.Websocket
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.modules.websockets.integration.TestWebSocket
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.Security

import org.junit.jupiter.api.Test

import java.net.http.WebSocketHandshakeException
import java.net.InetAddress

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class WebsocketTunnelTests {

    @Test
    fun testSecureUpstream() = test {
        TestHost(Layout.create().build(), false).use { upstreamServer ->
            val securePort = TestHost.nextPort()
            val cert = CertificateProvider.from(Security.getCertificate())

            upstreamServer.host.handler(
                Websocket.functional()
                    .onConnected { }
                    .onMessage { connection, message -> connection.write(message.data) }
                    .onClose { _, _ -> }
            ).bind(InetAddress.getByName("0.0.0.0"), securePort, cert, setOf(SslProtocol.Tls12))

            upstreamServer.start()

            val proxy = Proxy.create().upstream("https://localhost:$securePort")

            TestHost.run(proxy).use { runner ->
                // The proxy connects to the upstream over TLS using default (non-bypassed) certificate
                // validation, so a self-signed test certificate is correctly rejected as untrusted - this
                // exercises the TLS upgrade path in RawWebsocketConnection, not a successful tunnel.
                val exception = assertFailsWith<WebSocketHandshakeException> {
                    TestWebSocket.connect("ws://localhost:${runner.port}")
                }

                // counterpart of WebSocketError.NotAWebSocket: the answer is not a successful upgrade
                assertNotEquals(101, exception.response.statusCode())
            }
        }
    }

    @Test
    fun testBasics() = test {
        val (setup, port) = TestSetup.create()

        val client = TestWebSocket.connect("ws://localhost:$port")

        val message = "Hello from the other side?".toByteArray(Charsets.UTF_8)

        client.sendText(message, true)

        withTimeout(4000) {
            client.receive()
        }

        assertEquals("Hello from the other side?", String(message, Charsets.UTF_8))

        client.dispose()

        setup.close()
    }

    private class TestSetup private constructor(private val runner: TestHost, private val target: TestHost) : AutoCloseable {

        companion object {

            suspend fun create(): Pair<TestSetup, Int> {
                val upstreamServer = TestHost(Layout.create().build(), false)

                upstreamServer.host.handler(
                    Websocket.functional()
                        .onConnected { }
                        .onMessage { connection, message -> connection.write(message.data) }
                        .onClose { _, _ -> }
                ).start()

                // proxying server
                val proxy = Proxy.create()
                    .upstream("http://localhost:" + upstreamServer.port)

                val runner = TestHost(Layout.create().build())

                runner.host.handler(proxy)
                    .start()

                return Pair(TestSetup(runner, upstreamServer), runner.port)
            }

        }

        override fun close() {
            runner.close()
            target.close()
        }

    }

}
