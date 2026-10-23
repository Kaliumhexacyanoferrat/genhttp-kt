package org.genhttp.engine.netty.infrastructure.endpoints

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.Channel
import io.netty.channel.ChannelFactory
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.ChannelPipeline
import io.netty.channel.EventLoop
import io.netty.channel.EventLoopGroup
import io.netty.channel.ServerChannel
import io.netty.channel.socket.SocketProtocolFamily
import io.netty.channel.unix.UnixChannelOption
import io.netty.handler.timeout.IdleStateHandler
import io.netty.util.NetUtil

import org.genhttp.api.infrastructure.EndPoint
import org.genhttp.api.infrastructure.HttpProtocols
import org.genhttp.engine.netty.infrastructure.NettyServer
import org.genhttp.engine.netty.infrastructure.awaitDone
import org.genhttp.engine.netty.protocol.ConnectionHandler

import org.slf4j.Logger

import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.StandardProtocolFamily
import java.net.StandardSocketOptions
import java.nio.channels.ServerSocketChannel
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit

/**
 * An endpoint listening on a TCP port (`EndPoint` in GenHTTP).
 *
 * With the native transports, every event loop binds its own socket (`SO_REUSEPORT`) and serves the connections it
 * accepts, so the kernel spreads the connections over the loops. With NIO, a single socket accepts the connections
 * and distributes them over the loops.
 */
internal abstract class SocketEndPoint(
    protected val server: NettyServer,
    override val address: InetAddress?,
    override val port: Int,
    override val dualStack: Boolean
) : EndPoint {

    protected val logger: Logger = server.logging.getLogger(javaClass.name)

    private val channels = mutableListOf<Channel>()

    private val initializer = object : ChannelInitializer<Channel>() {

        override fun initChannel(channel: Channel) {
            if (!dualStack && rejects(channel)) {
                channel.close()
                return
            }

            val pipeline = channel.pipeline()

            configure(pipeline)

            val idleTimeout = server.options.tcp.idleTimeout.inWholeMilliseconds

            if (idleTimeout > 0) {
                pipeline.addLast(IdleStateHandler(0, 0, idleTimeout, TimeUnit.MILLISECONDS))
            }

            pipeline.addLast(ConnectionHandler(server, this@SocketEndPoint))
        }

    }

    override val protocols: HttpProtocols
        get() = HttpProtocols.Http1

    // region Functionality

    /**
     * Binds the endpoint on the given event loops.
     */
    suspend fun start(group: EventLoopGroup, loops: List<EventLoop>) {
        val address = address

        val family = when {
            address is Inet4Address && !dualStack -> SocketProtocolFamily.INET
            NetUtil.isIpV4StackPreferred() -> SocketProtocolFamily.INET
            else -> SocketProtocolFamily.INET6
        }

        // a native IPv6 socket bound to the IPv4 wildcard would only accept IPv4 clients
        val bindAddress = when {
            family == SocketProtocolFamily.INET -> address ?: InetAddress.getByName("0.0.0.0")
            address == null || address.isAnyLocalAddress -> InetAddress.getByName("::")
            else -> address
        }

        try {
            if (server.transports.bindPerLoop) {
                var endpoint = InetSocketAddress(bindAddress, port)

                if (port != 0) {
                    // sockets sharing a port are not detected by the kernel, so check whether the port is in use first
                    probe(family, endpoint)
                }

                for (loop in loops) {
                    val bound = bind(loop, loop, family, endpoint, true)

                    // an ephemeral port is chosen by the first socket and shared by the others
                    endpoint = InetSocketAddress(bindAddress, (bound.localAddress() as InetSocketAddress).port)
                }
            } else {
                bind(group, group, family, InetSocketAddress(bindAddress, port), false)
            }
        } catch (e: Exception) {
            close()
            throw BindingException("Failed to bind to ${address ?: "*"} on port $port.", e)
        }

        logger.info("Listening on {}:{} ({})", address ?: "*", port, describeSettings())
    }

    private fun probe(family: SocketProtocolFamily, endpoint: InetSocketAddress) {
        val protocolFamily = if (family == SocketProtocolFamily.INET) StandardProtocolFamily.INET else StandardProtocolFamily.INET6

        ServerSocketChannel.open(protocolFamily).use { socket ->
            socket.setOption(StandardSocketOptions.SO_REUSEADDR, true)
            socket.bind(endpoint)
        }
    }

    private suspend fun bind(parent: EventLoopGroup, child: EventLoopGroup, family: SocketProtocolFamily, endpoint: InetSocketAddress, reusePort: Boolean): Channel {
        val bootstrap = ServerBootstrap()
            .group(parent, child)
            .channelFactory(ChannelFactory<ServerChannel> { server.transports.newServerChannel(family) })
            .option(ChannelOption.SO_BACKLOG, server.options.tcp.listenBacklog)
            .option(ChannelOption.SO_REUSEADDR, true)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childOption(ChannelOption.ALLOW_HALF_CLOSURE, true)
            .childHandler(initializer)

        if (reusePort) {
            bootstrap.option(UnixChannelOption.SO_REUSEPORT, true)
        }

        val future = bootstrap.bind(endpoint).awaitDone()

        synchronized(channels) {
            channels.add(future.channel())
        }

        future.cause()?.let { throw it }

        return future.channel()
    }

    /**
     * Checks whether the given client uses a protocol family the endpoint does not accept (if dual stack is disabled).
     *
     * IPv6 only sockets cannot be created portably, so IPv4 clients are dropped by IPv6 endpoints. The io_uring
     * transport cannot create IPv4 sockets, so IPv6 clients are dropped by IPv4 endpoints.
     */
    private fun rejects(client: Channel): Boolean {
        val remote = (client.remoteAddress() as? InetSocketAddress)?.address ?: return false

        return isIPv4(remote) != (address is Inet4Address)
    }

    private fun isIPv4(remote: InetAddress): Boolean {
        if (remote is Inet4Address) {
            return true
        }

        // IPv4-mapped IPv6 address (::ffff:a.b.c.d)
        val bytes = remote.address

        return bytes.size == 16 && bytes.take(10).all { it == 0.toByte() } && bytes[10] == 0xFF.toByte() && bytes[11] == 0xFF.toByte()
    }

    /**
     * Adds the handlers required by the endpoint (e.g. TLS) to the pipeline of an accepted connection.
     */
    protected open fun configure(pipeline: ChannelPipeline) {
    }

    /**
     * Decides whether a client whose TLS handshake succeeded may use the connection.
     */
    open fun authenticate(certificate: X509Certificate?): Boolean = true

    protected open fun describeSettings(): String =
        "${if (secure) "HTTPS" else "HTTP"}, DualStack: $dualStack, Transport: ${server.transports.transport}, EventLoops: ${server.options.eventLoop.count}"

    // endregion

    // region Lifecycle

    override fun close() {
        val bound = synchronized(channels) {
            channels.toList().also { channels.clear() }
        }

        for (channel in bound) {
            try {
                channel.close()
            } catch (e: Exception) {
                logger.warn("Failed to dispose endpoint socket", e)
            }
        }
    }

    // endregion

}
