package org.genhttp.engine.ioxide.context

import ioxide.TcpConnection
import ioxide.tls.TlsService

import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

import org.genhttp.api.infrastructure.BufferSize
import org.genhttp.engine.ioxide.infrastructure.IoxideServer
import org.genhttp.engine.ioxide.infrastructure.ReactorState
import org.genhttp.engine.ioxide.infrastructure.Sockets
import org.genhttp.engine.ioxide.infrastructure.endpoints.SecureEndPoint
import org.genhttp.engine.ioxide.infrastructure.endpoints.SocketEndPoint
import org.genhttp.engine.ioxide.io.InboundChannel
import org.genhttp.engine.ioxide.io.OutboundChannel
import org.genhttp.engine.ioxide.io.UpgradableReader
import org.genhttp.engine.ioxide.io.WriteBufferPool
import org.genhttp.engine.ioxide.protocol.Http1Driver
import org.genhttp.engine.ioxide.security.TlsTransport
import org.genhttp.engine.shared.io.ConnectionReader
import org.genhttp.engine.shared.io.ConnectionWriter
import org.genhttp.engine.shared.types.ClientContext
import org.genhttp.engine.shared.types.HttpRequest
import org.genhttp.engine.shared.types.ResponseHandler

import java.net.InetAddress
import java.security.cert.X509Certificate

import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The state of a client connection (`ClientContext` in GenHTTP).
 *
 * The state (including the buffer of the reader and the request) is reused for the connections of a reactor, see
 * [ConnectionPool]: it is bound to a connection when the reactor accepted it and handed back to the pool once the
 * connection has been served.
 */
internal class ConnectionContext(
    override val server: IoxideServer,
    private val reactor: ReactorState,
    private val pool: ConnectionPool
) : ClientContext {

    /**
     * The data received from the client.
     */
    val inbound: InboundChannel = InboundChannel()

    /**
     * Passes the written data to the connection.
     */
    val outbound: OutboundChannel = OutboundChannel(reactor.buffers)

    /**
     * The reader of the connection, replaced by a new one after requests that required a larger buffer.
     */
    var reader: ConnectionReader = newReader()

    override val writer: ConnectionWriter = ConnectionWriter(outbound, WriteBufferPool.Size)

    /**
     * The request reused for all requests of the connection, replaced together with the [reader].
     */
    var request: HttpRequest = HttpRequest()

    val responseHandler: ResponseHandler = ResponseHandler(this)

    private val driver = Http1Driver(this)

    private var _connection: TcpConnection? = null

    private var _endPoint: SocketEndPoint? = null

    private var tls: TlsTransport? = null

    // when the TLS handshake of the connection expires (0 if none is running)
    private var handshakeDeadline = 0L

    // region Get-/Setters

    /**
     * The connection that is being served.
     */
    val connection: TcpConnection
        get() = _connection ?: throw IllegalStateException("The context is not bound to a connection")

    /**
     * The endpoint the connection has been accepted by.
     */
    val endPoint: SocketEndPoint
        get() = _endPoint ?: throw IllegalStateException("The context is not bound to a connection")

    /**
     * The certificate the client authenticated with, if any.
     */
    var clientCertificate: X509Certificate? = null
        private set

    /**
     * The address of the connected client, if known.
     */
    var remoteAddress: InetAddress? = null
        private set

    // endregion

    // region Functionality

    /**
     * Creates a reader for the connection (with a buffer of the default size).
     */
    fun newReader(): ConnectionReader = UpgradableReader(inbound, outbound, reactor.dispatcher)

    // endregion

    // region Lifecycle

    /**
     * Serves the given connection until it ends (on the reactor thread).
     */
    suspend fun serve(connection: TcpConnection, endPoint: SocketEndPoint) {
        _connection = connection
        _endPoint = endPoint

        inbound.bind(connection)
        outbound.bind(connection, reactor.dispatcher)

        pool.attached(this)

        try {
            if (!server.running) {
                // accepted while the server stops, after the connections of the reactor have been closed
                return
            }

            if (endPoint is SecureEndPoint) {
                val session = endPoint.accept(this) ?: return

                tls = session
                inbound.tls = session
                outbound.tls = session

                clientCertificate = session.clientCertificate
            }

            remoteAddress = try {
                Sockets.peerAddress(connection.clientFd)
            } catch (_: Exception) {
                null
            }

            driver.run()
        } finally {
            finish()
        }
    }

    // sends what is left and ends the TLS session, so that the connection can be closed
    private suspend fun finish() {
        try {
            try {
                // also releases a buffer the writer still holds
                writer.flush()
            } catch (_: Exception) {
                // the connection has been closed already
            }

            outbound.flush()
        } finally {
            val session = tls

            // the session is released even if the connection has been closed
            if (session != null) {
                tls = null

                try {
                    // the JDK stages its close notification, BoringSSL sends its own and releases the session
                    session.close(outbound)
                } catch (_: Exception) {
                    // the connection has been closed already
                } finally {
                    // nothing is encrypted with the session anymore, writes of other threads that arrive late fail
                    inbound.tls = null
                    outbound.seal()
                }

                try {
                    outbound.flush()

                    if (session.kernelTx) {
                        awaitDelivered()
                    }
                } catch (_: Exception) {
                    // the connection has been closed already
                }
            }

            pool.detached(this)
            recycle()
        }
    }

    // the kernel hands the records it encrypts to TCP when the socket can take them - possibly after the send of the
    // ring completed, and a moment after the send queue of the socket ran empty - and the FIN that follows when the
    // connection is released would overtake them, so the connection waits until its send queue stayed empty for a
    // while (there is no way to ask the kernel for the records it still holds)
    private suspend fun awaitDelivered() {
        val fd = connection.clientFd

        withTimeoutOrNull(DeliveryTimeout) {
            var quiet = 0

            while (!connection.isClosed && quiet < QuietChecks) {
                delay(QuietInterval)

                quiet = if (Sockets.unsent(fd) > 0) 0 else quiet + 1
            }
        }
    }

    /**
     * Closes the connection without sending the data that is still pending (on the reactor thread).
     */
    fun abort() {
        _connection?.let { Sockets.shutdown(it.clientFd, Sockets.SHUT_RDWR) }
    }

    private fun recycle() {
        inbound.reset()
        outbound.reset()

        if (reader.capacity > BufferSize.Read) {
            // the reader grew for a large request, its memory is not kept in the pool (the request references the
            // buffer as well)
            reader = newReader()
            request = HttpRequest()
        } else {
            reader.reset()

            try {
                request.reset()
            } catch (_: Exception) {
                request = HttpRequest()
            }
        }

        driver.reset()

        tls = null
        handshakeDeadline = 0L
        _connection = null
        _endPoint = null
        clientCertificate = null
        remoteAddress = null

        pool.release(this)
    }

    // endregion

    // region TLS

    /**
     * The TLS service that terminates the connections of the given port with BoringSSL on this reactor, if any.
     */
    fun tlsService(port: Int): TlsService? = reactor.tls[port]

    /**
     * Notes that the TLS handshake started, which needs to complete within the given time (on the reactor thread).
     */
    fun handshakeStarted(timeoutMs: Int) {
        handshakeDeadline = reactor.reactor.nowMs + timeoutMs
    }

    /**
     * Notes that the TLS handshake has been completed (or failed).
     */
    fun handshakeCompleted() {
        handshakeDeadline = 0L
    }

    /**
     * Closes the connection if its TLS handshake did not complete in time (on the reactor thread).
     */
    fun checkHandshake(now: Long) {
        if (handshakeDeadline in 1..now) {
            handshakeDeadline = 0L
            abort()
        }
    }

    // endregion

    private companion object {

        /**
         * How long a connection terminated with kernel TLS waits for its records to be delivered before it is closed.
         */
        val DeliveryTimeout = 10.seconds

        /**
         * The checks of the send queue a connection terminated with kernel TLS needs to find empty in a row.
         */
        const val QuietChecks = 10

        /**
         * The time between those checks.
         */
        val QuietInterval = 5.milliseconds

    }

}
