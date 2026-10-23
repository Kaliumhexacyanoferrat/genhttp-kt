package org.genhttp.engine.netty.context

import io.netty.channel.EventLoop

import org.genhttp.engine.netty.infrastructure.NettyServer

import java.util.ArrayDeque

/**
 * Reuses the state of the connections of an event loop (like the request pool per reactor of ioxide), so that
 * short-lived connections do not allocate the request, the reader with its buffer and the writer every time.
 *
 * The pool is only used on its event loop and keeps at most [MaxPooled] instances.
 */
internal class ConnectionPool(private val server: NettyServer, private val loop: EventLoop) {

    private val idle = ArrayDeque<ConnectionContext>()

    // the contexts attached to an open channel of the loop
    private val attached = HashSet<ConnectionContext>()

    /**
     * Returns a pooled context or a new one (on the event loop).
     */
    fun acquire(): ConnectionContext = idle.pollLast() ?: ConnectionContext(server, this)

    /**
     * Takes back a context that has been reset (on the event loop).
     */
    fun release(context: ConnectionContext) {
        if (idle.size < MaxPooled) {
            idle.addLast(context)
        }
    }

    /**
     * Registers a context that has been attached to a channel (on the event loop).
     */
    fun attached(context: ConnectionContext) {
        attached.add(context)
    }

    /**
     * Unregisters a context whose channel has been removed (on the event loop).
     */
    fun detached(context: ConnectionContext) {
        attached.remove(context)
    }

    /**
     * Closes the connections of the loop, as the server stops (on the event loop).
     *
     * The shutdown of a loop does not reliably close the channels registered with it: with a quiet period of zero,
     * the epoll and io_uring loops may terminate without closing them.
     */
    fun closeAll() {
        for (context in attached.toList()) {
            context.abort()
        }
    }

    /**
     * Whether the given thread is the one of the event loop the pool belongs to.
     */
    fun owns(thread: Thread): Boolean = loop.inEventLoop(thread)

    private companion object {

        const val MaxPooled = 1024

    }

}
