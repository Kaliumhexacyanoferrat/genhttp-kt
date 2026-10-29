package org.genhttp.engine.ioxide.context

import org.genhttp.engine.ioxide.infrastructure.IoxideServer
import org.genhttp.engine.ioxide.infrastructure.ReactorState

/**
 * Reuses the state of the connections of a reactor (like the request pool per reactor of ioxide), so that
 * short-lived connections do not allocate the request, the reader with its buffer and the writer every time.
 *
 * The pool is only used on its reactor and keeps at most [MaxPooled] instances.
 */
internal class ConnectionPool(private val server: IoxideServer, private val reactor: ReactorState) {

    private val idle = ArrayDeque<ConnectionContext>()

    // the contexts serving a connection of the reactor
    private val attached = HashSet<ConnectionContext>()

    /**
     * The number of connections that are being served (readable from any thread).
     */
    @Volatile
    var active: Int = 0
        private set

    /**
     * Returns a pooled context or a new one (on the reactor thread).
     */
    fun acquire(): ConnectionContext = idle.removeLastOrNull() ?: ConnectionContext(server, reactor, this)

    /**
     * Takes back a context that has been reset (on the reactor thread).
     */
    fun release(context: ConnectionContext) {
        if (idle.size < MaxPooled) {
            idle.addLast(context)
        }
    }

    /**
     * Registers a context that serves a connection (on the reactor thread).
     */
    fun attached(context: ConnectionContext) {
        attached.add(context)
        active = attached.size
    }

    /**
     * Unregisters a context whose connection has ended (on the reactor thread).
     */
    fun detached(context: ConnectionContext) {
        attached.remove(context)
        active = attached.size
    }

    /**
     * Closes the connections whose TLS handshake did not complete in time (on the reactor thread, once per tick).
     */
    fun sweepHandshakes(now: Long) {
        for (context in attached) {
            context.checkHandshake(now)
        }
    }

    /**
     * Closes the connections of the reactor, as the server stops (on the reactor thread).
     */
    fun closeAll() {
        for (context in attached.toList()) {
            context.abort()
        }
    }

    private companion object {

        const val MaxPooled = 1024

    }

}
