package org.genhttp.engine.netty

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How the engine itself is tuned, beneath what the bindings decide.
 *
 * @property eventLoop The event loops serving the connections
 * @property tcp The TCP listener and its TLS
 */
public data class EngineOptions(
    val eventLoop: EventLoopOptions = EventLoopOptions(),
    val tcp: TcpTransportOptions = TcpTransportOptions()
)

/**
 * The event loops: how many, and which transport they run on.
 *
 * @property count The number of event loops (one per CPU core by default)
 * @property transport The transport used by the event loops to perform I/O
 */
public data class EventLoopOptions(
    val count: Int = Runtime.getRuntime().availableProcessors(),
    val transport: Transport = Transport.Auto
) {

    init {
        require(count > 0) { "At least one event loop is required" }
    }

}

/**
 * The I/O transport used by the event loops.
 */
public enum class Transport {

    /** Uses epoll if available (Linux), NIO otherwise. */
    Auto,

    /** Native epoll transport (Linux only); every loop accepts on its own socket (`SO_REUSEPORT`). */
    Epoll,

    /** Native io_uring transport (Linux only); every loop accepts on its own socket (`SO_REUSEPORT`). */
    IoUring,

    /** The NIO transport of the JDK, available on every platform; a single socket accepts for all loops. */
    Nio

}

/**
 * The TCP listener and its TLS, from the listen backlog to the timeouts.
 *
 * @property handshakeTimeout The time a client has to complete the TLS handshake
 * @property listenBacklog The maximum number of pending connections queued by the operating system per socket
 * @property idleTimeout Close a connection that has neither received nor sent anything for this long.
 *                       [Duration.ZERO] (the default) disables it.
 *
 *                       Off by default because this listener also carries upgraded connections, and an idle
 *                       websocket is a healthy one. A timeout that is right for HTTP keep-alive is wrong for a
 *                       websocket whose ping interval is longer than that - the transport cannot tell the two
 *                       apart, so the framework does not guess on your behalf.
 *
 *                       Turn it on if you serve no upgrades, or set it comfortably above your websocket ping
 *                       interval so pings keep the connection marked alive.
 * @property sendTimeout Close a connection whose response has been outstanding for this long. [Duration.ZERO]
 *                       (the default) disables it.
 *
 *                       This is what bounds a client that stops reading: its window shuts, the send never
 *                       completes, and without a clock the connection is pinned for as long as the peer cares to
 *                       hold it. TCP will not end it on its own.
 *
 *                       Off by default because it is a deadline for the WHOLE response, not for progress within it,
 *                       so a large download to a slow client counts against it. Size it against your slowest
 *                       legitimate response rather than against a stall - for an API that serves small payloads,
 *                       something like 30 seconds is a straightforward win.
 */
public data class TcpTransportOptions(
    val handshakeTimeout: Duration = 10.seconds,
    val listenBacklog: Int = 1024,
    val idleTimeout: Duration = Duration.ZERO,
    val sendTimeout: Duration = Duration.ZERO
) {

    init {
        require(handshakeTimeout.isPositive()) { "The handshake timeout must be positive" }
        require(listenBacklog > 0) { "The listen backlog must be positive" }
        require(!idleTimeout.isNegative()) { "The idle timeout must not be negative" }
        require(!sendTimeout.isNegative()) { "The send timeout must not be negative" }
    }

}
