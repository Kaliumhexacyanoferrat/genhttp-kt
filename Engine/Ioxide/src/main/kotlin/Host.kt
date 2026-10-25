package org.genhttp.engine.ioxide

import ioxide.Reactor

import org.genhttp.api.infrastructure.ServerHost
import org.genhttp.engine.ioxide.infrastructure.IoxideServerHost

/**
 * Entry point for a server host running on the ioxide engine.
 *
 * The engine runs one io_uring reactor per CPU core (ioxide, Linux only). Every reactor accepts connections on its
 * own socket (`SO_REUSEPORT`) and serves them independently: a request is kept on the reactor that owns its
 * connection from start to finish, handlers are executed as coroutines on that reactor, timers (`delay`,
 * `withTimeout`) are submitted to its ring.
 *
 * Suspending code continues to work: libraries that complete their work on other threads (e.g. the JDK
 * `HttpClient` or `withContext(Dispatchers.IO)`) resume on a different thread, and the engine returns the coroutine
 * to the reactor that owns the connection. This is correct, but not free: the cost is incurred on every affected
 * call and, under load, reduces the very advantage the engine was adopted for.
 *
 * **Never block inside a request handler.** Calling `Thread.sleep`, `runBlocking`, `Future.get()` or blocking I/O
 * does not merely slow the reactor down, it halts it: every other connection served by the reactor waits until the
 * call returns, and a call waiting for work that only the blocked reactor can do never returns. Always suspend
 * instead. Request bodies are buffered completely before the handler is invoked, so reading them does not block.
 * Websockets run on the reactor as well (the upgraded stream is a `SuspendingInput`), so their handlers must not
 * block either; other content of upgraded connections that reads the stream blocking runs on a virtual thread.
 *
 * TLS is terminated by BoringSSL (the native library of ioxide, with optional kernel TLS) if available and able
 * to serve the endpoint, by the JDK otherwise (see [TcpTransportOptions.tlsProvider]).
 */
public object Host {

    /**
     * Creates a server host that runs on the ioxide engine.
     *
     * @param onReactorStart Runs once per reactor, on its thread, before the reactor accepts connections (e.g. to
     *                       register services that are bound to a reactor)
     * @param options The options to tune the engine with (defaults are used if not set)
     */
    public fun create(onReactorStart: ((Reactor) -> Unit)? = null, options: EngineOptions? = null): ServerHost =
        IoxideServerHost(onReactorStart, options)

}
