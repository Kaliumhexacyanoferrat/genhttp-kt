package org.genhttp.engine.ioxide.infrastructure

import ioxide.Reactor
import ioxide.tls.TlsService

import org.genhttp.engine.ioxide.context.ConnectionPool
import org.genhttp.engine.ioxide.io.WriteBufferPool

/**
 * What the server keeps per reactor: the dispatcher of its coroutines, the state of its connections and the TLS
 * services of the endpoints terminated with BoringSSL (by port).
 *
 * Only used on the thread of the reactor, which [current] returns it on.
 */
internal class ReactorState(server: IoxideServer, val reactor: Reactor, val tls: Map<Int, TlsService>) {

    val dispatcher: ReactorDispatcher = ReactorDispatcher(reactor)

    val buffers: WriteBufferPool = WriteBufferPool { dispatcher }

    val connections: ConnectionPool = ConnectionPool(server, this)

    companion object {

        private val bound = ThreadLocal<ReactorState?>()

        /**
         * The state of the reactor running on the calling thread, if any.
         */
        fun current(): ReactorState? = bound.get()

        /**
         * Binds the given state to the thread of its reactor.
         */
        fun bind(state: ReactorState) {
            bound.set(state)
        }

    }

}
