package org.genhttp.engine.netty

import io.netty.channel.EventLoop

import org.genhttp.api.infrastructure.ServerHost
import org.genhttp.engine.netty.infrastructure.NettyServerHost

/**
 * Entry point for a server host running on the Netty engine (the counterpart of the ioxide engine of GenHTTP).
 *
 * The engine runs one event loop per CPU core. Every loop accepts connections on its own socket (`SO_REUSEPORT`,
 * with the epoll and io_uring transports) and serves them independently: a request is kept on the loop that owns
 * its connection from start to finish, handlers are executed as coroutines on that loop.
 *
 * Suspending code continues to work: libraries that complete their work on other threads (e.g. the JDK
 * `HttpClient` or `withContext(Dispatchers.IO)`) resume on a different thread, and the engine returns the coroutine
 * to the loop that owns the connection. This is correct, but not free: the cost is incurred on every affected call
 * and, under load, reduces the very advantage the engine was adopted for.
 *
 * **Never block inside a request handler.** Calling `Thread.sleep`, `runBlocking`, `Future.get()` or blocking I/O
 * does not merely slow the loop down, it halts it: every other connection served by the loop waits until the call
 * returns, and a call waiting for work that only the blocked loop can do never returns. Always suspend instead.
 * Request bodies are buffered completely before the handler is invoked, so reading them does not block. Websockets
 * run on the event loop as well (the upgraded stream is a `SuspendingInput`), so their handlers must not block either;
 * other content of upgraded connections that reads the stream blocking runs on a virtual thread.
 *
 * TLS is terminated by OpenSSL (BoringSSL, shipped for Linux, macOS and Windows) if available, by the JDK otherwise
 * (see [TcpTransportOptions.tlsProvider]).
 */
public object Host {

    /**
     * Creates a server host that runs on the Netty engine.
     *
     * @param onEventLoopStart Runs once per event loop, on that loop, before the server accepts connections (e.g. to
     *                         register services that are bound to a loop)
     * @param options The options to tune the engine with (defaults are used if not set)
     */
    public fun create(onEventLoopStart: ((EventLoop) -> Unit)? = null, options: EngineOptions? = null): ServerHost =
        NettyServerHost(onEventLoopStart, options)

}
