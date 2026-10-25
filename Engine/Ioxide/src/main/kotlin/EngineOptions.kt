package org.genhttp.engine.ioxide

/**
 * How the engine itself is tuned, beneath what the bindings decide.
 *
 * @property reactor The io_uring reactors serving the connections
 * @property tcp The TCP listener and its TLS
 */
public data class EngineOptions(
    val reactor: ReactorOptions = ReactorOptions(),
    val tcp: TcpTransportOptions = TcpTransportOptions()
)

/**
 * The io_uring reactors: how many, and how much ring each one gets.
 *
 * @property reactorCount The number of reactors, each with a thread and a ring of its own (one per CPU core by
 *                        default)
 * @property ringEntries The submission queue entries of every ring (the kernel sizes the completion queue at twice
 *                       that)
 * @property recvBufferSize The size of the buffers the kernel receives into, shared by the connections of a reactor
 * @property recvSlots The number of those buffers per reactor, a power of two up to 32768
 */
public data class ReactorOptions(
    val reactorCount: Int = Runtime.getRuntime().availableProcessors(),
    val ringEntries: Int = 8192,
    val recvBufferSize: Int = 32 * 1024,
    val recvSlots: Int = 4096
) {

    init {
        require(reactorCount > 0) { "At least one reactor is required" }
        require(ringEntries > 0) { "The ring entries must be positive" }
        require(recvBufferSize > 0) { "The receive buffer size must be positive" }
        require(recvSlots > 0 && recvSlots and (recvSlots - 1) == 0 && recvSlots <= 32768) { "The receive slots must be a power of two up to 32768" }
    }

}

/**
 * The TCP listener and its TLS, from the listen backlog to the kTLS switches.
 *
 * @property handshakeTimeoutMs The time a client has to complete the TLS handshake, in milliseconds
 * @property cipherSuites The TLS 1.3 cipher suites to offer in preference order, separated by colons
 *                        (`TLS_AES_128_GCM_SHA256:TLS_AES_256_GCM_SHA384`), or null for the defaults of the provider
 * @property cipherList The TLS 1.2 ciphers to offer, separated by colons, or null for the defaults of the provider:
 *                      in the cipher-list syntax of BoringSSL (`ECDHE-RSA-AES128-GCM-SHA256`) for BoringSSL, as the
 *                      IANA names used by the JDK (`TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256`) for the JDK provider; the
 *                      server fails to start if the provider does not support a suite
 * @property txKernelTls The kernel encrypts outbound records (kTLS TX, BoringSSL only): TLS 1.3 with
 *                       `TLS_AES_128_GCM_SHA256` and without session tickets, requires the `tls` kernel module
 * @property rxKernelTls The kernel decrypts inbound records too (kTLS RX, experimental, requires [txKernelTls])
 * @property listenBacklog The maximum number of pending connections queued by the operating system per listener
 * @property writeSlabSize The initial size of the buffer every connection stages its responses in (it grows for
 *                         larger responses and shrinks back when the connection is reused)
 * @property poolMax The number of connections (with their buffers) a reactor keeps for reuse
 * @property recvQueueEntries The number of received buffers a connection can hold unread, a power of two (one more
 *                            closes the connection)
 * @property idleTimeoutMs Close a connection that waits for its client for this long. 0 (the default) disables it.
 *
 *                         Off by default because this listener also carries upgraded connections, and an idle
 *                         websocket is a healthy one. ioxide's own default is 60s, which is right for HTTP
 *                         keep-alive and wrong for a websocket whose ping interval is longer than that - the
 *                         transport cannot tell the two apart, so the framework does not guess on your behalf.
 *
 *                         Turn it on if you serve no upgrades, or set it comfortably above your websocket ping
 *                         interval so pings keep the connection marked alive.
 * @property sendTimeoutMs Close a connection whose response has been outstanding for this long. 0 (the default)
 *                         disables it.
 *
 *                         This is what bounds a client that stops reading: its window shuts, the send never
 *                         completes, and without a clock the connection is pinned for as long as the peer cares to
 *                         hold it. TCP will not end it on its own.
 *
 *                         Off by default because it is a deadline for the WHOLE response, not for progress within
 *                         it, so a large download to a slow client counts against it. Size it against your slowest
 *                         legitimate response rather than against a stall - for an API that serves small payloads,
 *                         something like 30s is a straightforward win.
 * @property tlsProvider The implementation TLS is terminated with
 */
public data class TcpTransportOptions(
    val handshakeTimeoutMs: Int = 10_000,
    val cipherSuites: String? = null,
    val cipherList: String? = null,
    val txKernelTls: Boolean = false,
    val rxKernelTls: Boolean = false,
    val listenBacklog: Int = 1024,
    val writeSlabSize: Int = 16 * 1024,
    val poolMax: Int = 1024,
    val recvQueueEntries: Int = 64,
    val idleTimeoutMs: Int = 0,
    val sendTimeoutMs: Int = 0,
    val tlsProvider: TlsProvider = TlsProvider.Auto
) {

    init {
        require(handshakeTimeoutMs > 0) { "The handshake timeout must be positive" }
        require(listenBacklog > 0) { "The listen backlog must be positive" }
        require(writeSlabSize > 0) { "The write slab size must be positive" }
        require(poolMax >= 0) { "The pool size must not be negative" }
        require(recvQueueEntries > 0 && recvQueueEntries and (recvQueueEntries - 1) == 0) { "The receive queue entries must be a power of two" }
        require(idleTimeoutMs >= 0) { "The idle timeout must not be negative" }
        require(sendTimeoutMs >= 0) { "The send timeout must not be negative" }
        require(!rxKernelTls || txKernelTls) { "Kernel TLS for received records requires kernel TLS for sent records" }
    }

}

/**
 * The implementation the TLS of secure endpoints is terminated with.
 */
public enum class TlsProvider {

    /**
     * Uses BoringSSL if its library is available and it can serve the endpoint (one certificate for every host, as
     * provided by `CertificateProvider.from(certificate)`, and no client certificates), the JDK provider otherwise.
     */
    Auto,

    /**
     * BoringSSL through ioxide (`libioxide_boringssl.so`, found via the `ioxide.boringssl` system property, the
     * `IOXIDE_BORINGSSL` environment variable or the library path), with optional kernel TLS; fails to start if the
     * library is not available or an endpoint needs what it does not support yet (certificates per host, client
     * certificates).
     */
    BoringSsl,

    /** The TLS implementation of the JDK (JSSE), with certificates per host and client certificates. */
    Jdk

}
