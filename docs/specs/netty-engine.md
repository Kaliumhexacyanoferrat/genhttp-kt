# Netty engine (port of `Engine/Ioxide`)

Upstream ships three engines: the internal one, Kestrel and ioxide. ioxide runs one `io_uring` reactor per core, keeps
a request on that core from start to finish and runs handlers on the reactor itself ("never block inside a request
handler"). Netty is the JVM counterpart: one event loop per core, sockets registered once with epoll (or io_uring),
handlers executed on the loop that owns the connection. `Engine/Netty` is therefore the port of `Engine/Ioxide`, with
ioxide replaced by Netty. Application code does not change: only the `Host` import does, as with upstream engines.

Why: the internal engine blocks a virtual thread per connection. Every keep-alive request costs an empty read, an
epoll re-registration and a hand-over through the JDK poller to a carrier thread. The handler pipeline itself is
already cheaper than Vert.x's (HttpArena pipelined: 3.7 vs 4.8 µs CPU per request), the round trip is not
(baseline: 33.5 vs 25.3 µs, latency-10k: 68 vs 31 µs). Netty removes exactly that round trip.

## Scope

Version 1: HTTP/1.1 (plain and TLS), keep-alive, pipelining, upgrades (websockets), the multi-engine acceptance suite.
Later: HTTP/2 (ALPN, h2c) and HTTP/3 (Netty QUIC codec, ioxide's `Http2Driver`/`Http3Driver`), a native file path
(`FileRegion`, like `FileAssetsHandler` switches to `ioxide.file`). Phase 2 below completes HTTP/1.1.

## Layout

| Upstream                                      | Kotlin                                                               |
|-----------------------------------------------|----------------------------------------------------------------------|
| `Engine/Ioxide` (`GenHTTP.Engine.Ioxide`)     | `Engine/Netty`, Gradle project `:engine-netty`, package `org.genhttp.engine.netty` |
| `Packages/GenHTTP.Core.Ioxide`, `Full.Ioxide` | `Packages/CoreNetty` (`:core-netty`), `Packages/FullNetty` (`:full-netty`) |
| `ServerEngine.Ioxide`                         | `ServerEngine.Netty` (new member of the API enum)                     |
| `Protocol/Drivers/Tcp/Http1Driver`            | `protocol/Http1Driver` (connection coroutine, see below)              |
| `Protocol/Responses/Http1Responder`, sinks    | reuse `ResponseHandler`/`ResponseSerializer`/sinks of `Engine/Shared` |
| `Infrastructure/Server`, `ServerHost`, endpoints | `infrastructure/NettyServer`, `NettyServerHost : AbstractServerHost`, endpoints |
| `EngineOptions` (+ `ReactorOptions`, `TcpTransportOptions`) | `EngineOptions` (+ `EventLoopOptions`, `TcpTransportOptions`), Netty specific |

Dependencies: `:engine-shared`, `:modules-io` (like `Engine/Internal`), Netty 4.2 (`netty-codec-http` is not needed
for HTTP/1.1, use `netty-handler` for TLS, `netty-transport`, `netty-transport-native-epoll` and
`netty-transport-native-io_uring` with the `linux-x86_64` and `linux-aarch_64` classifiers), all via
`gradle/libs.versions.toml` (Netty BOM `4.2.19.Final`). `Engine/Netty` must not depend on `Engine/Internal`.

## Public API

```kotlin
public object Host {
    /** Creates a server host that runs on the Netty engine. */
    public fun create(onEventLoopStart: ((EventLoop) -> Unit)? = null, options: EngineOptions? = null): ServerHost
}

public data class EngineOptions(
    val eventLoop: EventLoopOptions = EventLoopOptions(),
    val tcp: TcpTransportOptions = TcpTransportOptions()
)

public data class EventLoopOptions(
    val count: Int = Runtime.getRuntime().availableProcessors(),
    val transport: Transport = Transport.Auto          // Auto: epoll if available, NIO otherwise
)

public enum class Transport { Auto, Epoll, IoUring, Nio }

public data class TcpTransportOptions(
    val handshakeTimeout: Duration = 10.seconds,
    val cipherSuites: String? = null,                   // TLS 1.3 (OpenSSL format, JDK provider only)
    val cipherList: String? = null,                     // TLS 1.2 (OpenSSL format)
    val tlsProvider: TlsProvider = TlsProvider.Auto,    // Auto: OpenSSL (BoringSSL) if available, JDK otherwise
    val listenBacklog: Int = 1024,
    val idleTimeout: Duration = Duration.ZERO,          // ZERO disables it (ioxide default, see its KDoc)
    val sendTimeout: Duration = Duration.ZERO
)

public enum class TlsProvider { Auto, OpenSsl, Jdk }
```

`onEventLoopStart` mirrors `onReactorStart`: runs once per loop, on that loop, before the server accepts connections.
Port the KDoc of upstream `EngineOptions` where the option exists. `ServerHost` builder methods (`handler`, `bind`,
`compression`, `logging`, ...) come from `AbstractServerHost` unchanged.

## Execution model

- One `MultiThreadIoEventLoopGroup` per server with `count` loops (epoll / io_uring / NIO `IoHandlerFactory`).
  With epoll and io_uring every endpoint is bound once per loop with `SO_REUSEPORT`, so accepting is spread over
  all loops (ioxide: "accepting and serving connections independently"); with NIO it is bound once.
- Every connection runs one coroutine (the `Http1Driver`) on an `EventLoopDispatcher` of its loop:
  `isDispatchNeeded = !loop.inEventLoop()`, `dispatch` = `loop.execute`, and it implements `Delay` with
  `loop.schedule`, so `delay`/`withTimeout` stay on the loop (like `vertx.setTimer`). Coroutines that resume elsewhere
  (e.g. after `withContext(Dispatchers.IO)` or `HttpClient.sendAsync().await()`) are dispatched back to their loop,
  as ioxide returns work to the originating core.
- Handlers must not block. This is the documented contract of the engine (port the ioxide warning). Suspending is fine.
- The driver follows the structure of `ClientHandler` (internal engine) and upstream `Http1Driver`: wait for data,
  parse with `UltraHardenedParser.tryExtractFullHeader`, `HttpRequest.apply`, handle, write with `ResponseHandler`,
  `drain`/`reset`, continue with pipelined data or flush. "Wait for data" suspends the coroutine until `channelRead`
  delivers bytes (resumed inline on the loop). Same keep-alive, `Connection` header, HTTP/1.0 and error handling
  (`HttpParseException` → status of the exception, other errors → 500, then close) as `ClientHandler`.
- Reading: `channelRead` appends the received `ByteBuf` to the connection's inbound queue; the driver copies it into
  its `ConnectionReader` (through a non-blocking `ReadableByteChannel` adapter, `fill` is only called when data is
  queued). Pause `autoRead` while more than a threshold (e.g. 64 KB) is queued, resume when the driver waits again.
- Writing: `ClientContext.writer` is a `ConnectionWriter` over a `WriteTarget` (Engine/Shared) that rents 16 KB
  buffers from the channel's allocator (larger buffers are not pooled by Netty's adaptive allocator) and
  `ctx.write`s them without copying; responses written while the loop delivers a read are flushed in
  `channelReadComplete` (one flush per batch), others by a flush task. Writes on the loop never wait: a large response
  is queued in Netty's outbound buffer (ioxide `WriteOverflowStrategy.Grow`). Instead, the driver does not handle the
  next request while the channel is not writable (suspends until it is), so a client that pipelines requests without
  reading the responses is pushed back by TCP instead of filling the memory. `Connection.Close`: flush, then close
  after the write completed.
- The state of a connection (request, reader with its buffer, writer, driver) is pooled per event loop (at most
  1024, like ioxide's request pool per reactor) and reused once the driver has finished and the channel is removed.
  A reader that grew for a large request is not pooled (neither is the request that references its buffer).
- Request bodies are buffered completely before the handler is invoked, because `RequestBody.asStream()` returns a
  blocking `InputStream` that cannot wait for data on the loop (the bytes can only arrive through that same loop).
  Content-Length: wait until `length` bytes are buffered. Chunked: wait until a scanner has seen the terminating chunk
  and trailers (on malformed framing dispatch anyway, `ChunkedBodyStream` reports the error). The reader buffer grows
  for large bodies; after such a request replace it by a fresh default-sized reader so memory is not held.
- Upgrades (`Connection.Upgrade`): the response is written on a new virtual thread. The stream returned by
  `request.upgrade()` implements `SuspendingInput` (API): content that supports it (the websockets module) switches to
  the loop with `withContext(input.dispatcher)` and reads with `readSuspending`, which suspends until the loop
  delivers data (resumed inline). Other content (e.g. the reverse proxy tunnel) reads blocking on the virtual thread,
  which waits until the loop delivers data or the channel closes; its writes are `writeAndFlush`ed and wait (park)
  while the channel is not writable. Writes on the loop never wait, `readSuspending` waits while the channel is not
  writable instead (a client that sends frames without reading the answers is pushed back). Writes issued by another
  event loop (a websocket broadcasting to the connections of other loops) do not wait either, as blocking that loop
  would halt its connections.

## TLS

- `SniHandler` with a mapping that builds (and caches per returned entry) an `SslContext` from
  `CertificateProvider.provide(host)`; protocols from the endpoint's `SecurityConfiguration`; the provider from
  `TcpTransportOptions.tlsProvider` (OpenSSL via `netty-tcnative-boringssl-static` if available, the JDK otherwise).
  Handshake timeout from `TcpTransportOptions.handshakeTimeout`.
- Ciphers: `cipherList` (TLS 1.2) and `cipherSuites` (TLS 1.3) in OpenSSL or IANA notation, unsupported names stop the
  server at startup; BoringSSL cannot restrict the TLS 1.3 suites, so `cipherSuites` only applies to the JDK provider
  (a warning is logged otherwise).
- Client certificates: `clientAuth` REQUIRE/OPTIONAL from the validator, validation through the same logic as
  `Engine/Internal`'s `ValidatingTrustManager` (copy it, it is internal there). The validated peer certificate is
  passed to `HttpRequest.apply`. Unlike ioxide (OpenSSL wants trust anchors up front), plain validators work, so the
  ioxide special case in `ClientCertificateAuthenticationTests` does not apply.

## Endpoints and lifecycle

- Same binding semantics as `SocketEndPoint` (address, port, dual stack incl. the IPv4-on-IPv6-only deviation,
  `BindingException` message), endpoints report `HttpProtocols.Http1`.
- `start()`: prepare the handler (same log line as `ThreadedServer`), create the group, run `onEventLoopStart` on every
  loop, bind all endpoints, log `Listening on ...` per endpoint. `close()`: stop accepting, close the connections of
  every loop (tracked per loop: with a quiet period of 0, epoll and io_uring loops may terminate without closing their
  channels), cancel the server scope, shut the group down gracefully (quiet period 0). Connections closed by a
  timeout or the shutdown are closed from the head of the pipeline, skipping the TLS handler, which would wait for its
  close notification to be sent to a client that does not read. `serverEngine` = `ServerEngine.Netty`.
- `Date` and `Server` headers through `DateHeader`/`ServerHeader` of `Engine/Shared`.

## Tests and tooling

- `TestHost.createHost`: `ServerEngine.Netty -> org.genhttp.engine.netty.Host.create()`.
- `MultiEngineTest.engines()`: `Internal` and `Netty` by default (upstream runs Internal, Kestrel, Ioxide),
  `TEST_ENGINE` selects one. Every `@MultiEngineTest` must pass on both engines without changes to the tests.
- `Benchmarks/.../ArenaServer.kt` takes the engine as an argument, so both engines can be load tested.

## Phase 2: complete HTTP/1.1

The goal is an engine that can be compared with Vert.x and Micronaut on every HTTP/1.1 profile of HttpArena
(baseline, pipelined, limited-conn, latency-*, async, json-comp, json-tls, static-tls, 8gbit, echo-ws-*). HTTP/2 and
HTTP/3 stay out of scope.

- **Responses straight into Netty buffers.** Today `ConnectionWriter` collects a response in a pooled direct buffer
  and `OutboundChannel` copies it into a Netty buffer. Instead the writer fills buffers rented from the channel's
  allocator and hands them to Netty without copying. This needs a small, behavior-neutral abstraction in
  `Engine/Shared` (where the writer gets and sends its buffers); the internal engine keeps its current behavior.
- **Flush with the read batch.** Responses written while the loop handles a read are flushed in
  `channelReadComplete`; only responses written outside a read (resumed after a timer or another dispatcher) need a
  flush task.
- **Per-loop pools.** Requests, readers (with their 16 KB buffers) and the other per-connection objects are reused
  per event loop (like ioxide's request pool per reactor), so short-lived connections (limited-conn) do not allocate
  them for every connection.
- **Read path.** Measure the copy from the received `ByteBuf` into the reader. Only avoid it if the measurement shows
  it matters (Glyph11 recycles its views only for array-backed buffers).
- **OpenSSL.** TLS uses `netty-tcnative-boringssl-static` (natives for the platforms of the epoll/io_uring natives
  plus macOS and Windows) when it is available, the JDK provider otherwise. `TcpTransportOptions` gains a
  `tlsProvider` (`Auto`, `OpenSsl`, `Jdk`) and the cipher options of ioxide (`CipherSuites` for TLS 1.3,
  `CipherList` for TLS 1.2) where Netty can apply them. SNI, client certificates and every validator keep working
  with both providers.
- **Websockets.** Measure echo throughput of the upgraded connection on both engines. The measurement showed a large
  regression of the virtual thread bridge (an echo cost about twice the CPU of the internal engine), so websockets run
  on the event loop through the `SuspendingInput` API addition (see Execution model).
- **Coverage.** Engine-specific behavior that the multi-engine tests do not reach (transports, TLS providers,
  send/idle timeouts, backpressure, `onEventLoopStart`) gets tests without upstream counterpart in
  `Testing/Acceptance/src/test/kotlin/additional/netty/` (see PORTING.md), written separately from the implementation.

## Documentation

`docs/DEVIATIONS.md` (Ioxide engine ported as Netty engine, `ServerEngine.Netty`, Netty-specific options, buffered
request bodies, upgrades on virtual threads, HTTP/1.1 only for now), `docs/PORTING.md` (layout table, the execution
model per engine), `docs/SYNC.md` (upstream `Engine/Ioxide` changes map to `Engine/Netty` by intent), `docs/PORT.md`,
README (choosing an engine), `tools/api_parity_exclusions.txt` if the parity tool needs it.
