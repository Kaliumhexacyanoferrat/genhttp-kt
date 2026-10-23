package org.genhttp.engine.netty.context

import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext

import org.genhttp.api.infrastructure.BufferSize
import org.genhttp.engine.netty.infrastructure.NettyServer
import org.genhttp.engine.netty.infrastructure.endpoints.SocketEndPoint
import org.genhttp.engine.netty.io.InboundChannel
import org.genhttp.engine.netty.io.OutboundChannel
import org.genhttp.engine.netty.io.UpgradableReader
import org.genhttp.engine.netty.protocol.Http1Driver
import org.genhttp.engine.shared.io.ConnectionReader
import org.genhttp.engine.shared.io.ConnectionWriter
import org.genhttp.engine.shared.types.ClientContext
import org.genhttp.engine.shared.types.HttpRequest
import org.genhttp.engine.shared.types.ResponseHandler

import java.net.InetAddress
import java.net.InetSocketAddress
import java.security.cert.X509Certificate

/**
 * The state of a client connection (`ClientContext` in GenHTTP).
 *
 * The state (including the buffer of the reader and the request) is reused for the connections of an event loop,
 * see [ConnectionPool]: it is attached to a channel when the channel is initialized and handed back to the pool
 * once the driver has finished and the channel has been removed.
 */
internal class ConnectionContext(override val server: NettyServer, private val pool: ConnectionPool) : ClientContext {

    /**
     * The data received from the client that has not been read yet.
     */
    val inbound: InboundChannel = InboundChannel()

    /**
     * Passes the written data to the channel.
     */
    val outbound: OutboundChannel = OutboundChannel(server.options.tcp.sendTimeout.inWholeMilliseconds)

    /**
     * The reader of the connection, replaced by a new one after requests that required a larger buffer.
     */
    var reader: ConnectionReader = newReader()

    override val writer: ConnectionWriter = ConnectionWriter(outbound, OutboundChannel.BufferSize)

    /**
     * The request reused for all requests of the connection, replaced together with the [reader].
     */
    var request: HttpRequest = HttpRequest()

    val responseHandler: ResponseHandler = ResponseHandler(this)

    private val driver = Http1Driver(this)

    private var _endPoint: SocketEndPoint? = null

    private var _channel: Channel? = null

    // the instance is handed back to the pool when both are done
    private var attached = false

    private var running = false

    // region Get-/Setters

    /**
     * The endpoint the connection has been accepted by.
     */
    val endPoint: SocketEndPoint
        get() = _endPoint ?: throw IllegalStateException("The context is not attached to a connection")

    /**
     * The Netty channel of the connection.
     */
    val channel: Channel
        get() = _channel ?: throw IllegalStateException("The context is not attached to a connection")

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
    fun newReader(): ConnectionReader = UpgradableReader(inbound, outbound) { server.dispatcher(channel.eventLoop()) }

    // endregion

    // region Lifecycle

    /**
     * Attaches the context to the channel of the given handler context (on the event loop of the channel).
     */
    fun attach(context: ChannelHandlerContext, endPoint: SocketEndPoint) {
        _endPoint = endPoint
        _channel = context.channel()

        inbound.bind(context.channel())
        outbound.bind(context)

        attached = true

        pool.attached(this)

        if (!server.running) {
            // accepted while the server stops, after the connections of the loop have been closed
            abort()
        }
    }

    /**
     * Starts serving the connection.
     */
    fun start(clientCertificate: X509Certificate?) {
        val channel = channel

        this.clientCertificate = clientCertificate
        remoteAddress = (channel.remoteAddress() as? InetSocketAddress)?.address

        running = true

        server.launch(channel.eventLoop()) {
            try {
                driver.run()
            } finally {
                running = false
                recycleIfDone()
            }
        }
    }

    /**
     * Detaches the context from its channel, which has been removed (on the event loop).
     */
    fun detach() {
        inbound.release()

        attached = false
        pool.detached(this)

        recycleIfDone()
    }

    /**
     * Closes the connection without sending the data that is still pending (on any thread).
     */
    fun abort() {
        outbound.abort()
    }

    private fun recycleIfDone() {
        // the driver completes on another thread if the loop has been shut down, the state is dropped then
        if (attached || running || !pool.owns(Thread.currentThread())) {
            return
        }

        try {
            // a buffer the writer still holds is released by the closed channel
            writer.flush()
        } catch (_: Exception) {
            // the connection has been closed already
        }

        if (!outbound.reset()) {
            // writes of a virtual thread are still pending
            return
        }

        inbound.reset()

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

        _endPoint = null
        _channel = null
        clientCertificate = null
        remoteAddress = null

        pool.release(this)
    }

    // endregion

}
