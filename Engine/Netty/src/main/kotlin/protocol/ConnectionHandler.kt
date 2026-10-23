package org.genhttp.engine.netty.protocol

import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.socket.ChannelInputShutdownEvent
import io.netty.handler.ssl.SslHandler
import io.netty.handler.ssl.SslHandshakeCompletionEvent
import io.netty.handler.timeout.IdleStateEvent
import io.netty.util.ReferenceCountUtil

import org.genhttp.engine.netty.context.ConnectionContext
import org.genhttp.engine.netty.infrastructure.NettyServer
import org.genhttp.engine.netty.infrastructure.endpoints.SocketEndPoint
import org.genhttp.engine.netty.io.InboundChannel
import org.genhttp.engine.netty.io.OutboundChannel
import org.genhttp.engine.shared.types.ConnectionExceptions

import java.net.InetSocketAddress
import java.security.cert.X509Certificate
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Connects a Netty channel with the [Http1Driver] serving it.
 *
 * The handler queues the received data for the driver and notifies it about the end of the input, the writability
 * of the channel and the outcome of the TLS handshake. The driver is started as soon as the connection is ready (for
 * secure endpoints, after the handshake succeeded and the client has been authenticated).
 */
internal class ConnectionHandler(private val server: NettyServer, private val endPoint: SocketEndPoint) : ChannelInboundHandlerAdapter() {

    private var inbound: InboundChannel? = null

    private var outbound: OutboundChannel? = null

    private var started = false

    // region Lifecycle

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        inbound = InboundChannel(ctx.channel())
        outbound = OutboundChannel(ctx, server.options.tcp.sendTimeout.inWholeMilliseconds)
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        inbound?.release()
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        if (!endPoint.secure) {
            start(ctx, null)
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        inbound?.complete(closed = true)
        outbound?.writabilityChanged()
    }

    private fun start(ctx: ChannelHandlerContext, clientCertificate: X509Certificate?) {
        val inbound = inbound ?: return
        val outbound = outbound ?: return

        started = true

        val channel = ctx.channel()

        val remoteAddress = (channel.remoteAddress() as? InetSocketAddress)?.address

        val context = ConnectionContext(server, endPoint, channel, inbound, outbound, clientCertificate, remoteAddress)

        server.launch(channel.eventLoop()) {
            Http1Driver(context).run()
        }
    }

    // endregion

    // region Events

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val inbound = inbound

        if (msg is ByteBuf && inbound != null) {
            inbound.offer(msg)
        } else {
            ReferenceCountUtil.release(msg)
        }
    }

    override fun channelWritabilityChanged(ctx: ChannelHandlerContext) {
        outbound?.writabilityChanged()
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        when (evt) {
            is SslHandshakeCompletionEvent -> handshakeCompleted(ctx, evt)

            // the client will not send more data, but still reads the responses
            is ChannelInputShutdownEvent -> inbound?.complete(closed = false)

            is IdleStateEvent -> ctx.close()

            else -> ctx.fireUserEventTriggered(evt)
        }
    }

    private fun handshakeCompleted(ctx: ChannelHandlerContext, event: SslHandshakeCompletionEvent) {
        if (!event.isSuccess) {
            // reported via exceptionCaught (unless the client went away or the handshake timed out)
            ctx.close()
            return
        }

        val certificate = try {
            ctx.pipeline().get(SslHandler::class.java)?.engine()?.session?.peerCertificates?.firstOrNull() as? X509Certificate
        } catch (_: SSLPeerUnverifiedException) {
            null
        }

        if (!endPoint.authenticate(certificate)) {
            ctx.close()
            return
        }

        start(ctx, certificate)
    }

    override fun exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
        if (!ConnectionExceptions.isGracefulDisconnect(cause)) {
            val message = if (started || !endPoint.secure) "Failed to handle client connection" else "Failed to authenticate client connection"

            server.logging.getLogger(ConnectionHandler::class.java.name).warn(message, cause)
        }

        ctx.close()
    }

    // endregion

}
