package org.genhttp.testing.acceptance.additional.netty

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeout

import org.genhttp.api.content.Handler
import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.CertificateProvider
import org.genhttp.api.infrastructure.HttpProtocols
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.infrastructure.ServerEngine
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.engine.netty.EngineOptions
import org.genhttp.engine.netty.EventLoopOptions
import org.genhttp.engine.netty.Transport
import org.genhttp.modules.io.Content
import org.genhttp.modules.io.content
import org.genhttp.modules.io.fromString
import org.genhttp.testing.TestHost
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.Security

import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

import org.slf4j.event.Level

import java.net.Socket
import java.net.SocketTimeoutException

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Not ported from GenHTTP: the lifecycle of a server on the Netty engine. The server identifies its engine, its
 * endpoints serve HTTP/1.1 only (whatever they have been bound with), a handler that fails to prepare is logged like
 * on the internal engine, and stopping the server closes all connections: those waiting for their next request, for
 * a handler that is suspended or for data on an upgraded connection.
 */
class ServerTests {

    @Test
    fun testServerDescribesItself() = test {
        NettyRunner(Content.from(Resource.fromString("Hello World!"))).use { runner ->
            val secure = TestHost.nextPort()

            runner.host.bind(null, runner.port)
            runner.host.bind(null, secure, CertificateProvider.from(Security.getCertificate()))

            runner.start()

            val server = assertNotNull(runner.host.instance)

            assertEquals(ServerEngine.Netty, server.serverEngine)
            assertTrue(server.running)

            val endPoints = server.endPoints.associateBy { it.port }

            assertEquals(setOf(runner.port, secure), endPoints.keys)

            assertFalse(endPoints.getValue(runner.port).secure)
            assertTrue(endPoints.getValue(secure).secure)

            // the secure endpoint has been bound with HTTP/1.1 and HTTP/2 (the default)
            assertTrue(endPoints.values.all { it.protocols == HttpProtocols.Http1 })

            runner.host.stop()

            assertFalse(server.running)
        }
    }

    @Test
    fun testFailingPreparationIsLogged() = test {
        NettyRunner.run(FailingPreparation()).use { runner ->
            val failure = runner.logging.entries.firstOrNull { it.level == Level.ERROR && it.message == "Failed to prepare the handler chain" }

            assertEquals("The handler cannot be prepared", failure?.throwable?.message)

            // the server is running anyway
            RawClient.connect(runner.port).use { client ->
                RawClient.send(client, RawClient.request())

                assertEquals("Prepared or not", RawClient.readResponse(client.getInputStream()).text)
            }
        }
    }

    /**
     * Stops servers with connections in every state several times, as the connections are closed by the shutdown
     * of the event loops, which happens concurrently with the work they are doing.
     */
    @ParameterizedTest
    @MethodSource("transports")
    fun testStopClosesConnections(transport: Transport) = test {
        repeat(25) { attempt ->
            val handler = StateHandler()

            val runner = NettyRunner.run(handler, EngineOptions(eventLoop = EventLoopOptions(count = 2, transport = transport)))

            runner.use {
                val idle = RawClient.connect(runner.port)
                val waiting = RawClient.connect(runner.port)
                val upgraded = RawClient.connect(runner.port)

                try {
                    RawClient.send(idle, RawClient.request("/idle"))
                    assertEquals("Hello World!", RawClient.readResponse(idle.getInputStream()).text)

                    RawClient.send(waiting, RawClient.request("/suspend"))
                    withTimeout(10.seconds) { handler.suspended.await() }

                    UpgradeHandler.upgrade(upgraded, "/upgrade")
                    withTimeout(10.seconds) { handler.reading.await() }

                    val stopping = TimeSource.Monotonic.markNow()

                    runner.host.stop()

                    assertTrue(stopping.elapsedNow() < 5.seconds, "Stopping the server took ${stopping.elapsedNow()}")

                    for ((name, client) in listOf("idle" to idle, "waiting" to waiting, "upgraded" to upgraded)) {
                        assertClosed(client, "Attempt $attempt: the $name connection has not been closed by the stop")
                    }

                    // the content reading the upgraded connection sees the end of its input
                    assertEquals(-1, withTimeout(10.seconds) { handler.ended.await() })
                } finally {
                    listOf(idle, waiting, upgraded).forEach(Socket::close)
                }
            }
        }
    }

    // region Helpers

    private fun assertClosed(client: Socket, message: String) {
        client.soTimeout = 2000

        try {
            assertEquals(-1, client.getInputStream().read(), message)
        } catch (_: SocketTimeoutException) {
            fail(message)
        }
    }

    /**
     * Answers requests to `/idle`, suspends requests to `/suspend` until they are cancelled and reads upgraded
     * connections of `/upgrade` blocking on a virtual thread.
     */
    private class StateHandler : Handler {

        val suspended = CompletableDeferred<Unit>()

        val reading = CompletableDeferred<Unit>()

        val ended = CompletableDeferred<Int>()

        private val upgrade = UpgradeHandler { request, _ ->
            val input = request.upgrade()

            reading.complete(Unit)

            ended.complete(input.read())
        }

        override suspend fun prepare(server: Server) {
        }

        override suspend fun handle(request: Request): Response? = when (request.header.path.toString()) {
            "/suspend" -> {
                suspended.complete(Unit)
                awaitCancellation()
            }

            "/upgrade" -> upgrade.handle(request)

            else -> request.respond().content("Hello World!").build()
        }

    }

    private class FailingPreparation : Handler {

        override suspend fun prepare(server: Server) {
            throw IllegalStateException("The handler cannot be prepared")
        }

        override suspend fun handle(request: Request): Response = request.respond().content("Prepared or not").build()

    }

    companion object {

        @JvmStatic
        fun transports(): List<Transport> = availableTransports()

    }

    // endregion

}
