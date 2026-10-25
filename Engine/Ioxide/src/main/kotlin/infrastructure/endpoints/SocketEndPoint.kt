package org.genhttp.engine.ioxide.infrastructure.endpoints

import org.genhttp.api.infrastructure.EndPoint
import org.genhttp.api.infrastructure.HttpProtocols
import org.genhttp.engine.ioxide.infrastructure.IoxideServer

import org.slf4j.Logger

import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.StandardProtocolFamily
import java.net.StandardSocketOptions
import java.nio.channels.ServerSocketChannel

/**
 * An endpoint listening on a TCP port (`EndPoint` in GenHTTP).
 *
 * Every reactor binds its own socket on the port (`SO_REUSEPORT`) and serves the connections it accepts, so the
 * kernel spreads the connections over the reactors. Like upstream, a connection is matched to its endpoint by the
 * port it arrived on, and every listener is bound to all interfaces (`[::]` with dual stack, `0.0.0.0` otherwise).
 */
internal abstract class SocketEndPoint(
    protected val server: IoxideServer,
    override val address: InetAddress?,
    port: Int,
    override val dualStack: Boolean
) : EndPoint {

    protected val logger: Logger = server.logging.getLogger(javaClass.name)

    /**
     * The port of the endpoint (a free one chosen when the server starts, if 0 has been configured).
     */
    final override var port: Int = port
        private set

    override val protocols: HttpProtocols
        get() = HttpProtocols.Http1

    // region Functionality

    /**
     * Checks that the port can be bound (sockets sharing a port with `SO_REUSEPORT` would not detect a port that is
     * already in use) and chooses a free port if none has been configured.
     *
     * @throws BindingException if the port is in use
     */
    fun probe() {
        try {
            bindProbe().use { socket ->
                port = (socket.localAddress as InetSocketAddress).port
            }
        } catch (e: Exception) {
            throw BindingException("Failed to bind to ${address ?: "*"} on port $port.", e)
        }

        val address = address

        if (address != null && !address.isAnyLocalAddress) {
            logger.warn("The ioxide engine binds every endpoint to all interfaces, the address {} of the endpoint on port {} is not applied", address, port)
        }
    }

    /**
     * Whether the port of the endpoint can be bound (again).
     */
    fun released(): Boolean = try {
        bindProbe().close()
        true
    } catch (_: Exception) {
        false
    }

    private fun bindProbe(): ServerSocketChannel {
        val family = if (dualStack) StandardProtocolFamily.INET6 else StandardProtocolFamily.INET
        val wildcard = InetAddress.getByName(if (dualStack) "::" else "0.0.0.0")

        val socket = ServerSocketChannel.open(family)

        try {
            socket.setOption(StandardSocketOptions.SO_REUSEADDR, true)
            socket.bind(InetSocketAddress(wildcard, port))
        } catch (e: Exception) {
            socket.close()
            throw e
        }

        return socket
    }

    /**
     * Logs that the endpoint accepts connections.
     */
    fun announce() {
        logger.info("Listening on {}:{} ({})", address ?: "*", port, describeSettings())
    }

    protected open fun describeSettings(): String =
        "${if (secure) "HTTPS" else "HTTP"}, DualStack: $dualStack, Reactors: ${server.options.reactor.reactorCount}"

    // endregion

    // region Lifecycle

    override fun close() {
        // the listeners are owned by the reactors
    }

    // endregion

}
