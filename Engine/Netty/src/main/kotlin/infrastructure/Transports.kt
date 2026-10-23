package org.genhttp.engine.netty.infrastructure

import io.netty.channel.IoHandlerFactory
import io.netty.channel.ServerChannel
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollIoHandler
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.nio.NioIoHandler
import io.netty.channel.socket.SocketProtocolFamily
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.uring.IoUring
import io.netty.channel.uring.IoUringIoHandler
import io.netty.channel.uring.IoUringServerSocketChannel

import org.genhttp.engine.netty.Transport

import java.nio.channels.spi.SelectorProvider

/**
 * The transport the event loops of a server run on, resolved from the requested [Transport].
 *
 * @property transport The transport actually used (never [Transport.Auto])
 * @property ioHandlerFactory Creates the I/O handlers of the event loops
 */
internal class Transports private constructor(
    val transport: Transport,
    val ioHandlerFactory: IoHandlerFactory,
    private val serverChannelFactory: (SocketProtocolFamily) -> ServerChannel
) {

    /**
     * Whether every event loop binds its own socket (`SO_REUSEPORT`), so that the kernel spreads incoming connections
     * over the loops. With NIO, a single socket accepts the connections for all loops.
     */
    val bindPerLoop: Boolean
        get() = transport != Transport.Nio

    /**
     * Creates a server socket channel of the given protocol family.
     */
    fun newServerChannel(family: SocketProtocolFamily): ServerChannel = serverChannelFactory(family)

    companion object {

        /**
         * Resolves the given transport, failing if a native transport has been requested that is not available.
         */
        fun resolve(requested: Transport): Transports = when (requested) {
            Transport.Auto -> if (Epoll.isAvailable()) epoll() else nio()

            Transport.Epoll -> {
                if (!Epoll.isAvailable()) {
                    throw UnsupportedOperationException("The epoll transport is not available on this system", Epoll.unavailabilityCause())
                }

                epoll()
            }

            Transport.IoUring -> {
                if (!IoUring.isAvailable()) {
                    throw UnsupportedOperationException("The io_uring transport is not available on this system", IoUring.unavailabilityCause())
                }

                // io_uring sockets are created for the preferred protocol family of the system
                Transports(Transport.IoUring, IoUringIoHandler.newFactory()) { IoUringServerSocketChannel() }
            }

            Transport.Nio -> nio()
        }

        private fun epoll() = Transports(Transport.Epoll, EpollIoHandler.newFactory()) { EpollServerSocketChannel(it) }

        private fun nio() = Transports(Transport.Nio, NioIoHandler.newFactory()) { NioServerSocketChannel(SelectorProvider.provider(), it) }

    }

}
