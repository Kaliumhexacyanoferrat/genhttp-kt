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
import org.genhttp.engine.shared.types.ConnectionExceptions

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

    private var context: ConnectionContext? = null

    private var started = false

    // region Lifecycle

    override fun handlerAdded(ctx: ChannelHandlerContext) {
        val context = server.connections(ctx.channel().eventLoop()).acquire()

        context.attach(ctx, endPoint)

        this.context = context
    }

    override fun handlerRemoved(ctx: ChannelHandlerContext) {
        context?.detach()
        context = null
    }

    override fun channelActive(ctx: ChannelHandlerContext) {
        if (!endPoint.secure) {
            start(null)
        }
    }

    override fun channelInactive(ctx: ChannelHandlerContext) {
        context?.let {
            it.inbound.complete(closed = true)
            it.outbound.writabilityChanged()
        }
    }

    private fun start(clientCertificate: X509Certificate?) {
        val context = context ?: return

        started = true

        context.start(clientCertificate)
    }

    // endregion

    // region Events

    override fun channelRead(ctx: ChannelHandlerContext, msg: Any) {
        val context = context

        if (msg is ByteBuf && context != null) {
            // responses written by the driver (resumed inline) are flushed when the read completes
            context.outbound.readStarted()
            context.inbound.offer(msg)
        } else {
            ReferenceCountUtil.release(msg)
        }
    }

    override fun channelReadComplete(ctx: ChannelHandlerContext) {
        context?.outbound?.readCompleted()
    }

    override fun channelWritabilityChanged(ctx: ChannelHandlerContext) {
        context?.outbound?.writabilityChanged()
    }

    override fun userEventTriggered(ctx: ChannelHandlerContext, evt: Any) {
        when (evt) {
            is SslHandshakeCompletionEvent -> handshakeCompleted(ctx, evt)

            // the client will not send more data, but still reads the responses
            is ChannelInputShutdownEvent -> context?.inbound?.complete(closed = false)

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

        start(certificate)
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
