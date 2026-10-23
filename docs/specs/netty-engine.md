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
(`FileRegion`, like `FileAssetsHandler` switches to `ioxide.file`), an OpenSSL provider.

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
    val listenBacklog: Int = 1024,
    val idleTimeout: Duration = Duration.ZERO,          // ZERO disables it (ioxide default, see its KDoc)
    val sendTimeout: Duration = Duration.ZERO
)
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
- Writing: `ClientContext.writer` is a `ConnectionWriter` over a `WritableByteChannel` that copies into pooled Netty
  buffers and `ctx.write`s them; the driver calls `ctx.flush()` when no pipelined data is left (one flush per batch,
  as Netty's `channelReadComplete`). Writes never wait: a large response is queued in Netty's outbound buffer
  (ioxide `WriteOverflowStrategy.Grow`). `Connection.Close`: flush, then close after the write completed.
- Request bodies are buffered completely before the handler is invoked, because `RequestBody.asStream()` returns a
  blocking `InputStream` that cannot wait for data on the loop (the bytes can only arrive through that same loop).
  Content-Length: wait until `length` bytes are buffered. Chunked: wait until a scanner has seen the terminating chunk
  and trailers (on malformed framing dispatch anyway, `ChunkedBodyStream` reports the error). The reader buffer grows
  for large bodies; after such a request replace it by a fresh default-sized reader so memory is not held.
- Upgrades (`Connection.Upgrade`, websockets read frames from the blocking `request.upgrade()` stream): the response
  is written on a new virtual thread. The inbound adapter then blocks that virtual thread until the loop delivers
  data or the channel closes, outbound writes are `writeAndFlush`ed and wait (park) while the channel is not writable.
  This bridge only exists for upgraded connections.

## TLS

- `SniHandler` with a mapping that builds (and caches per returned entry) an `SslContext` from
  `CertificateProvider.provide(host)`; protocols from the endpoint's `SecurityConfiguration`; `SslProvider.JDK`
  (OpenSSL later). Handshake timeout from `TcpTransportOptions.handshakeTimeout`.
- Client certificates: `clientAuth` REQUIRE/OPTIONAL from the validator, validation through the same logic as
  `Engine/Internal`'s `ValidatingTrustManager` (copy it, it is internal there). The validated peer certificate is
  passed to `HttpRequest.apply`. Unlike ioxide (OpenSSL wants trust anchors up front), plain validators work, so the
  ioxide special case in `ClientCertificateAuthenticationTests` does not apply.

## Endpoints and lifecycle

- Same binding semantics as `SocketEndPoint` (address, port, dual stack incl. the IPv4-on-IPv6-only deviation,
  `BindingException` message), endpoints report `HttpProtocols.Http1`.
- `start()`: prepare the handler (same log line as `ThreadedServer`), create the group, run `onEventLoopStart` on every
  loop, bind all endpoints, log `Listening on ...` per endpoint. `close()`: stop accepting, close idle connections,
  cancel the server scope, shut the group down gracefully (quiet period 0). `serverEngine` = `ServerEngine.Netty`.
- `Date` and `Server` headers through `DateHeader`/`ServerHeader` of `Engine/Shared`.

## Tests and tooling

- `TestHost.createHost`: `ServerEngine.Netty -> org.genhttp.engine.netty.Host.create()`.
- `MultiEngineTest.engines()`: `Internal` and `Netty` by default (upstream runs Internal, Kestrel, Ioxide),
  `TEST_ENGINE` selects one. Every `@MultiEngineTest` must pass on both engines without changes to the tests.
- `Benchmarks/.../ArenaServer.kt` takes the engine as an argument, so both engines can be load tested.

## Documentation

`docs/DEVIATIONS.md` (Ioxide engine ported as Netty engine, `ServerEngine.Netty`, Netty-specific options, buffered
request bodies, upgrades on virtual threads, HTTP/1.1 only for now), `docs/PORTING.md` (layout table, the execution
model per engine), `docs/SYNC.md` (upstream `Engine/Ioxide` changes map to `Engine/Netty` by intent), `docs/PORT.md`,
README (choosing an engine), `tools/api_parity_exclusions.txt` if the parity tool needs it.
