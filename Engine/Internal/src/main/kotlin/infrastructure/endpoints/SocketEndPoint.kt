package org.genhttp.engine.internal.infrastructure.endpoints

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

import org.genhttp.api.infrastructure.EndPoint
import org.genhttp.api.infrastructure.HttpProtocols
import org.genhttp.api.infrastructure.Server
import org.genhttp.engine.internal.context.ConnectionContext
import org.genhttp.engine.shared.types.ConnectionExceptions

import org.slf4j.Logger

import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.StandardProtocolFamily
import java.net.StandardSocketOptions
import java.nio.channels.ByteChannel
import java.nio.channels.ClosedChannelException
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.security.cert.X509Certificate

/**
 * An endpoint listening on a TCP port (`EndPoint` in GenHTTP).
 *
 * The endpoint accepts connections on a virtual thread and handles every connection as a coroutine within the
 * scope of the server, which runs on virtual threads as well.
 */
internal abstract class SocketEndPoint(
    protected val server: Server,
    override val address: InetAddress?,
    override val port: Int,
    override val dualStack: Boolean
) : EndPoint {

    protected val logger: Logger = server.logging.getLogger(javaClass.name)

    private var socket: ServerSocketChannel? = null

    private var listener: Job? = null

    @Volatile
    private var shuttingDown = false

    override val protocols: HttpProtocols
        get() = HttpProtocols.Http1

    // region Functionality

    fun start(scope: CoroutineScope) {
        val channel = try {
            open().apply {
                bind(InetSocketAddress(address, port), 2048)
            }
        } catch (e: Exception) {
            throw BindingException("Failed to bind to ${address ?: "*"} on port $port.", e)
        }

        socket = channel

        logger.info("Listening on {}:{} ({})", address ?: "*", port, describeSettings())

        listener = scope.launch {
            listen(channel, scope)
        }
    }

    private fun open(): ServerSocketChannel {
        val family = when {
            address is Inet4Address && !dualStack -> StandardProtocolFamily.INET
            address == null && !dualStack -> StandardProtocolFamily.INET6
            else -> null
        }

        val channel = if (family != null) ServerSocketChannel.open(family) else ServerSocketChannel.open()

        channel.setOption(StandardSocketOptions.SO_REUSEADDR, true)

        return channel
    }

    private val rejectsIPv4: Boolean
        get() = !dualStack && address !is Inet4Address

    private fun isIPv4(client: SocketChannel): Boolean {
        val remote = (client.remoteAddress as? InetSocketAddress)?.address ?: return false

        if (remote is Inet4Address) {
            return true
        }

        // IPv4-mapped IPv6 address (::ffff:a.b.c.d)
        val bytes = remote.address

        return bytes.size == 16 && bytes.take(10).all { it == 0.toByte() } && bytes[10] == 0xFF.toByte() && bytes[11] == 0xFF.toByte()
    }

    protected open fun describeSettings(): String = "${if (secure) "HTTPS" else "HTTP"}, DualStack: $dualStack"

    private suspend fun listen(channel: ServerSocketChannel, scope: CoroutineScope) {
        while (!shuttingDown && scope.isActive) {
            try {
                val client = channel.accept()

                if (rejectsIPv4 && isIPv4(client)) {
                    // the JDK cannot create IPv6-only sockets, so IPv4 clients are dropped if dual stack is disabled
                    client.close()
                    continue
                }

                scope.launch {
                    accept(client)
                }
            } catch (_: ClosedChannelException) {
                break
            } catch (e: Exception) {
                if (shuttingDown) {
                    break
                }

                if (!ConnectionExceptions.isGracefulDisconnect(e)) {
                    logger.error("Failed to accept incoming connection", e)
                }

                delay(500)
            }
        }
    }

    protected abstract suspend fun accept(client: SocketChannel)

    protected suspend fun handle(client: SocketChannel, channel: ByteChannel, clientCertificate: X509Certificate? = null) {
        client.setOption(StandardSocketOptions.TCP_NODELAY, true)

        val context = ConnectionContext(server, this, client, channel, clientCertificate)

        context.clientHandler.run()
    }

    // endregion

    // region Lifecycle

    override fun close() {
        shuttingDown = true

        try {
            socket?.close()
        } catch (e: Exception) {
            logger.warn("Failed to dispose endpoint socket", e)
        }

        socket = null
        listener = null
    }

    // endregion

}
