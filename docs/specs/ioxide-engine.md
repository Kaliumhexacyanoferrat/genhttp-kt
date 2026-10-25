# ioxide engine (port of `Engine/Ioxide`)

Upstream's ioxide engine runs on [ioxide](https://github.com/MDA2AV/ioxide): one `io_uring` reactor per core, a
request kept on that core from start to finish, handlers run on the reactor ("never block inside a request handler").
The Netty engine was written as its JVM counterpart because ioxide only existed for .NET. ioxide now has a Kotlin port
of its own (`kotlin/ioxide`: the TCP runtime and TLS on BoringSSL with kernel TLS, `io_uring` through the Foreign
Function & Memory API, handlers as suspend functions resumed inline by the reactor), so `Engine/Ioxide` is a port of
upstream `Engine/Ioxide` on the same runtime. The Netty engine stays: it is portable, ioxide is Linux only.

## Scope

Version 1: HTTP/1.1 (plain and TLS), keep-alive, pipelining, upgrades (websockets), the multi-engine acceptance suite on
Linux. Not part of ioxide's Kotlin port yet, and therefore not of the engine: HTTP/2, HTTP/3 (QUIC), the native file path
(`ioxide.file`), certificates per host (SNI), client certificates and certificate rotation in BoringSSL. The engine
serves those endpoints with the JDK's TLS instead (see TLS).

## Layout

| Upstream                                                   | Kotlin                                                                    |
|------------------------------------------------------------|---------------------------------------------------------------------------|
| `Engine/Ioxide` (`GenHTTP.Engine.Ioxide`)                  | `Engine/Ioxide`, Gradle project `:engine-ioxide`, package `org.genhttp.engine.ioxide` |
| `ioxide` NuGet package (MDA2AV/ioxide)                     | `Ioxide`, Gradle project `:ioxide` (`ioxide-kt`), ioxide's Kotlin port, package `ioxide` |
| `Packages/GenHTTP.Core.Ioxide`, `Full.Ioxide`              | `Packages/CoreIoxide` (`:core-ioxide`), `Packages/FullIoxide` (`:full-ioxide`) |
| `ServerEngine.Ioxide`                                      | `ServerEngine.Ioxide`                                                     |
| `Protocol/Drivers/Tcp/TcpDriver`                           | `context/ConnectionContext.serve` (TLS, peer address) and `protocol/Http1Driver` |
| `Protocol/Responses/Http1Responder`, sinks                 | `ResponseHandler`/`ResponseSerializer`/sinks of `Engine/Shared`           |
| `Infrastructure/Server`, `ServerHost`, endpoints           | `infrastructure/IoxideServer`, `IoxideServerHost : AbstractServerHost`, endpoints |
| `EngineOptions` (+ `ReactorOptions`, `TcpTransportOptions`) | `EngineOptions` (+ `ReactorOptions`, `TcpTransportOptions`, `TlsProvider`) |

The runtime is not published to Maven Central yet, so `Ioxide/` holds the sources of `kotlin/ioxide` as upstream has
them (`ioxide.commit` in `upstream.properties`), plus dual-stack listeners (`ServerConfig.dualStack`, an option of the
C# runtime the Kotlin port did not have yet). Once ioxide publishes its Kotlin port, `:ioxide` becomes a dependency.

## Public API

```kotlin
public object Host {
    /** Creates a server host that runs on the ioxide engine. */
    public fun create(onReactorStart: ((Reactor) -> Unit)? = null, options: EngineOptions? = null): ServerHost
}

public data class EngineOptions(val reactor: ReactorOptions = ReactorOptions(), val tcp: TcpTransportOptions = TcpTransportOptions())

public data class ReactorOptions(reactorCount, ringEntries, recvBufferSize, recvSlots)          // upstream defaults

public data class TcpTransportOptions(
    handshakeTimeoutMs, cipherSuites, cipherList, txKernelTls, rxKernelTls, listenBacklog, writeSlabSize, poolMax,
    recvQueueEntries, idleTimeoutMs, sendTimeoutMs,                                              // upstream names and defaults
    tlsProvider: TlsProvider = TlsProvider.Auto                                                  // Auto, BoringSsl, Jdk
)
```

Upstream's names, types (milliseconds as `Int`) and defaults are kept, including the KDoc of the timeouts (disabled by
default, as the listener carries upgraded connections). Options of upstream the Kotlin runtime does not have are not
offered (`ReactorOptions.Incremental`, `WriteOverflow`, `ZeroCopySend`, `QuicTransportOptions`, `Http3Options`).

## Execution model

- `ReactorOptions.reactorCount` reactors, each on a platform thread `ioxide-genhttp-<id>` with its own ring, its own
  `SO_REUSEPORT` listener per port and its own share of the provided receive buffers. `onReactorStart` runs on the
  reactor's thread after its listeners are bound and before it accepts (connections wait in the backlog meanwhile).
- Every connection runs as a coroutine of the server's scope on a `ReactorDispatcher`: `isDispatchNeeded` is
  `!reactor.onReactorThread`, `dispatch` is `reactor.post`, `Delay` is a `RingTimer` (`IORING_OP_TIMEOUT`), so
  `delay`/`withTimeout` stay on the ring. The reactor resumes a connection inline from the completion that delivered
  its data; coroutines that resume elsewhere are handed back. Handlers must not block.
- The driver follows upstream's `Http1Driver` (and the Netty port of it): wait for data, parse with
  `UltraHardenedParser`, `HttpRequest.apply`, handle, write with `ResponseHandler`, `drain`/`reset`, continue with
  pipelined data or wait. Same keep-alive, `Connection` header, HTTP/1.0 and error handling as the other engines.
- Reading: the connection's `InboundChannel` copies (or decrypts) the received buffers into the `ConnectionReader` and
  hands them back to the reactor right away, so a connection whose handler waits holds none. Reads never wait, the
  driver only reads after the channel reported data.
- Writing: `OutboundChannel` is the `WriteTarget` of the connection's `ConnectionWriter`: rented buffers are copied (or
  encrypted) into the connection's write slab, which is sent once the reactor has handled its current completions (one
  send for all pipelined responses) or as soon as 64 KB are staged. A connection has one send in flight: what is
  written meanwhile is queued and staged when the send completed. The driver does not handle more requests while more
  than 64 KB wait to be sent. Writes from other threads are handed to the reactor and wait until sent; writes of other
  reactors are queued without waiting.
- Request bodies are buffered completely before the handler runs (like the Netty engine): `RequestBody.asStream()`
  returns a blocking `InputStream`, and the bytes can only arrive through the reactor the handler runs on.
- Upgrades (`Connection.Upgrade`): the response is written on a virtual thread; the upgraded stream implements
  `SuspendingInput`, so the websockets module runs its message pump on the reactor and reads with `readSuspending`,
  other content reads blocking on its virtual thread (the read is handed to the reactor).
- The state of a connection (request, reader with its buffer, writer, driver) is pooled per reactor (at most 1024).

## TLS

- `TlsProvider.BoringSsl`: ioxide's `TlsService` per reactor and port (BoringSSL from `libioxide_boringssl.so`, found via
  `-Dioxide.boringssl`, `IOXIDE_BORINGSSL` or the library path), with the certificate of the endpoint as PEM, ALPN
  `http/1.1`, the TLS floor from the endpoint's protocols, `cipherSuites`/`cipherList` and kTLS (`txKernelTls`,
  `rxKernelTls`). It serves endpoints with one certificate for every host (`CertificateProvider.from`) and no client
  certificates only, and refuses others at start.
- `TlsProvider.Jdk`: an `SSLEngine` per connection on top of the ring: certificates per host through the endpoint's
  `CertificateProvider` (SNI), client certificates through its `CertificateValidator` (copies of the internal
  engine's key and trust managers), the handshake timeout enforced by a sweep per reactor tick.
- `TlsProvider.Auto` (default): BoringSSL where it is available and can serve the endpoint, the JDK otherwise.
- A connection terminated with kernel TLS waits (up to 10 s) until the send queue of its socket stayed empty for 50 ms
  before it is released: the kernel hands the records it encrypted to TCP when the socket can take them, even after
  the send of the ring completed, and the FIN ioxide sends when the connection is released would overtake them. The
  kernel does not tell how many records it still holds, so this is a mitigation in the engine of what belongs into
  ioxide.

## Endpoints and lifecycle

- Like upstream: every listener is bound to all interfaces (`[::]` dual-stack or `0.0.0.0`; an endpoint's address is
  not applied, which is logged), all endpoints share one dual-stack mode and every endpoint has a port of its own (a
  connection is matched to its endpoint by the port it arrived on), endpoints report `HttpProtocols.Http1`.
- `start()`: prepare the handler, refuse other platforms than Linux, probe every port without `SO_REUSEPORT`
  (`BindingException`, a free port for port 0), start the reactors and wait until they listen (a failing hook or
  reactor fails the start), log `Listening on ...` per endpoint.
- `close()`: close the connections of every reactor (`shutdown`, which completes their receives), cancel the handlers
  still suspended, stop the reactors and join their threads, then wait until the ports can be bound again (the kernel
  releases the listeners of a closed ring asynchronously).

## Tests and tooling

- `TestHost.createHost`: `ServerEngine.Ioxide -> org.genhttp.engine.ioxide.Host.create()`.
- `MultiEngineTest.engines()`: `Internal` and `Netty`, plus `Ioxide` on Linux (upstream: `Internal`, `Kestrel`, plus
  `Ioxide` on Linux); `TEST_ENGINE=Ioxide` elsewhere fails like upstream.
- `Testing/Acceptance/src/test/kotlin/additional/ioxide/`: engine-specific behavior (reactors, endpoints, options, TLS
  providers, timeouts, upgrades, connections). On other platforms they check that the engine refuses to start; where
  BoringSSL is not available, that requesting it refuses to start.
- `Benchmarks/.../ArenaServer.kt` takes `ioxide` as the engine (`IOXIDE_TLS_PROVIDER`, `IOXIDE_KERNEL_TLS=1`).
