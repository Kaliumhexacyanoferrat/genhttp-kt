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
 * @property cipherSuites The TLS 1.3 cipher suites to offer, separated by colons in OpenSSL format (e.g.
 *                        `TLS_AES_128_GCM_SHA256:TLS_CHACHA20_POLY1305_SHA256`), or null for the defaults of the
 *                        provider. BoringSSL (the OpenSSL shipped with the engine) does not allow to configure them,
 *                        so they only apply to the JDK provider.
 * @property cipherList The cipher suites to offer for TLS 1.2, separated by colons in OpenSSL format (e.g.
 *                      `ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256`), or null for the defaults of
 *                      the provider. The IANA names used by the JDK (`TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256`) are
 *                      accepted as well, the server fails to start if the provider does not support a suite.
 * @property tlsProvider The implementation TLS is terminated with
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
    val cipherSuites: String? = null,
    val cipherList: String? = null,
    val tlsProvider: TlsProvider = TlsProvider.Auto,
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

/**
 * The implementation the TLS of secure endpoints is terminated with.
 */
public enum class TlsProvider {

    /** Uses OpenSSL if available (BoringSSL is shipped for Linux, macOS and Windows), the JDK provider otherwise. */
    Auto,

    /** OpenSSL (BoringSSL via netty-tcnative), fails to start if it is not available on the platform. */
    OpenSsl,

    /** The TLS implementation of the JDK (JSSE). */
    Jdk

}
