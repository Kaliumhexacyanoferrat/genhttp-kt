# Deviation log

Every place where genhttp-kt differs from GenHTTP *in spirit or behavior* is listed here with its reason.

| Area | Deviation | Reason |
|------|-----------|--------|
| Naming | Interfaces drop the `I` prefix; factory clashes resolved via companion extensions, implementation clashes via descriptive prefixes | Kotlin convention (decided by the maintainer) |
| Naming | `Async` suffix dropped, methods are `suspend` | Kotlin coroutine convention |
| Generators | `[MemoryView]` source generator replaced by the `MemoryView` base class; the views (`ByteString`, `ContentType`, ...) are classes instead of structs and compute their hash on first use | No source generators needed on the JVM; no user-defined value types on the JVM (Kotlin value classes wrap a single value and are boxed when used as keys or through interfaces) |
| Engines | Only the internal engine is ported; `ServerEngine` contains `Internal` and `Custom` | Kestrel and ioxide are .NET specific |
| Adapters | `GenHTTP.Adapters.AspNetCore` is not ported | ASP.NET Core does not exist on the JVM |
| I/O | `Stream` → `InputStream`/`OutputStream`, `IBufferWriter` → `WritableByteChannel`, `PipeReader` → `InputStream` | JVM standard types, usable from coroutines because connections run on virtual threads |
| Memory | `ReadOnlyMemory<byte>` → read-only `ByteBuffer` | JDK standard memory view |
| Certificates | `X509Certificate2` → `KeyStore.PrivateKeyEntry`; .NET TLS enums replaced by small API enums | The JDK separates keys and certificates |
| Reflection | Roslyn code generation replaced by kotlin-reflect; inline handlers take suspend lambdas or function references | See [Reflection](PORTING.md#reflection) |
| Reflection | Parameter default values are not readable; optional parameters are omitted instead | kotlin-reflect limitation |
| Resources | `FromAssembly` → `fromClasspath` | No assemblies on the JVM |
| Templates | Cottle syntax replaced by Pebble syntax for `Renderer.from(...)` templates | No Cottle implementation on the JVM |
| Glyph11 | Parses contiguous `ByteBuffer`s only (no `ReadOnlySequence` overloads) | The engine always provides a contiguous buffer |
| API | `IHandlerBuilder<TBuilder>` → `HandlerBuilder.WithConcerns<TBuilder>` | Kotlin cannot overload an interface name by generic arity |
| API | `Forwarding.For` → `Forwarding.forAddress` | `for` is a hard keyword in Kotlin |
| API | `DocumentationAttribute` base class → `@Documentation` meta-annotation; `OperationDocumentation.entries` is a `List<Annotation>` | Kotlin annotations cannot inherit |
| API | `RequestBodyAttribute`, `RequestHeaderAttribute`, `ResponseAttribute`, `ResponseHeaderAttribute` → `@ApiRequestBody`, `@ApiRequestHeader`, `@ApiResponse`, `@ApiResponseHeader` | Avoid clashes with `RequestBody`, `RequestHeader`, `Response` |
| API | Optional annotation parameters use `""` / `Nothing::class` instead of `null`; `ResponseHeaderAttribute.Status` is an array with at most one entry | Annotation parameters cannot be nullable |
| API | `SslProtocols`, `SslPolicyErrors`, `X509RevocationMode`, `CompressionLevel` are small enums in the API; flags become `Set<E>` | No JDK equivalents |
| API | `HttpProtocols` keeps its combined members and gains `hasFlag`, `plus`, `minus` | Replaces `[Flags]` arithmetic |
| API | `ResponseStatus` carries its numeric `code`, `ResponseStatus.fromCode(Int)` replaces casts | Kotlin enums have no implicit integer value |
| API | `IServer.DisposeAsync()` → `suspend fun close()`; `IEndPoint : IDisposable` → `EndPoint : AutoCloseable` | Kotlin lifecycle conventions |
| API | `IPropertyBag.TryGet<T>(key, out T)` → `tryGet(key, KClass<T>): T?` plus a reified `tryGet<T>(key)` extension | No out parameters |
| API | `MemoryView == ReadOnlyMemory` → `contentEquals(ByteBuffer)` | Kotlin `==` only compares with `Any?` |
| Engine | Read timeouts (initial request, keep-alive, TLS handshake) are enforced by a watchdog that checks the deadlines of waiting connections once per second and closes expired connections, instead of a cancellation token per read; timeouts fire up to a second late | A timer per read costs a timer to schedule and cancel on every read, publishing a deadline is a single atomic update; reads block their virtual thread, so they are aborted by closing the connection anyway |
| Engine | Endpoints with `dualStack = false` on IPv6 accept IPv4 connections at the TCP level but close them immediately | The JDK cannot set `IPV6_V6ONLY`; the binding tests therefore check that a request is answered instead of a bare TCP connect |
| Tests | `ConnectionTests.testUpgrade` reads the 101 response from a raw socket | The JDK HTTP client rejects unexpected 101 responses |
| Tests | `ResourceTreeTests.testByAssembly` checks that the classpath root contains the `Resources` folder instead of exactly one node | The classpath root merges all roots of the runtime, an assembly has a single root |
| Conversion | JSON escapes HTML-sensitive and non-ASCII characters as unicode escapes, enums with explicit values (`ResponseStatus`, `Priority`) are written as their value | Mirrors the defaults of System.Text.Json |
| Conversion | XML element names are written in PascalCase and read case-insensitively; JSON writes whole-number floating point values without fraction (`42`) | Mirrors XmlSerializer (.NET property names) and System.Text.Json |
| Tests | The acceptance tests are friends of selected modules (`friendPaths`) to access internals | Counterpart of `InternalsVisibleTo` |
| Reflection | Kotlin types of lambda parameters are converted with `KType.toJavaType()` (Conversion) | kotlin-reflect cannot map types of lambdas to Java types |
| IO | `FormattableContent<T>` merged into `FormattableContent(value: Any)` (formats via `toString()`, doubles like .NET) | No generic arity overloading, no `IUtf8SpanFormattable` |
| IO | `WritingStream` is a write-only `OutputStream(WritableByteChannel, Flushable)` | JVM streams are not bidirectional |
| IO | Stream checksums require `markSupported()`, `StreamContent.length` is only the known length | JVM streams are not seekable |
| IO | `Handler.From` / `Concern.From` sync and async overloads merged into one suspend lambda | A suspend lambda accepts both |
| IO | `DirectoryContainer` rejects paths that escape the directory (e.g. via `%2F`) | Hardening |
| IO | `ResponseBuilder.apply(modifications)` shadows the stdlib `apply` for response builders | Upstream name kept |
| Websockets | `WebsocketConnection` is not disposable; the `Canceled` frame error is never produced (cancellation throws `CancellationException`); segmented payloads are linearized into a copy | `close()` clashes with the close frame API and the engine owns the input; no `ReadOnlySequence` on the JVM |
| Websockets | Status codes of close frames are `Int` (validated to 0..65535), opcodes are `Byte` | No unsigned types in the public API |
| I18n | Overloads of `fromRequest` are resolved by the lambda return type (`@OverloadResolutionByLambdaReturnType`), the culture variant is `fromRequestCultures` on the JVM | Kotlin cannot overload lambdas by return type otherwise |
| I18n | The selected culture is available via `CurrentCulture.culture()` / `uiCulture()` (coroutine context element `CultureContext`) instead of `CultureInfo.CurrentCulture` | The JVM has no per-thread culture; coroutines may switch threads |
| I18n | Language tags without region resolve to a default region (`de` → `de-DE`) using a built-in table | Mirrors `CultureInfo.CreateSpecificCulture` |
| Authentication | Lambda overloads of `authentication(...)` with two parameters need explicit parameter types at the call site | Kotlin does not resolve overloads by the lambda body |
| Authentication | Bearer tokens are `com.nimbusds.jwt.SignedJWT`, keys are `java.security.Key`; unsigned (`alg: none`) and encrypted tokens are rejected | Nimbus JOSE replaces System.IdentityModel; hardening |
| ServerSentEvents | `EventStream` rethrows `CancellationException` instead of sending a retry | Structured concurrency |
| ApiBrowsing | The browser templates (`Index.html`) use Pebble syntax (`{{ title }}`) instead of Cottle | Pebble replaces Cottle (see Pages) |
| LoadBalancing | `PriorityEvaluation` is a typealias `(Request) -> Priority` | Delegates map to function types |
| Engine | Implementation clashes in Engine/Shared: `ServerHost` → `AbstractServerHost`, `ClientConnection` → `RemoteClientConnection`, `KeyValueList` → `GlyphKeyValueList`, `PropertyBag` → `MapPropertyBag` | The API interfaces use these names (`I` prefix dropped) |
| Files | `FileAssetsBuilder.refreshInterval(...)` is accepted but has no effect | Upstream it only affects the Ioxide engine |
| DependencyInjection | Koin replaces Microsoft.Extensions.DependencyInjection (`IServiceProvider` → `Koin`, `IServiceScope` → `Scope`); scoped services are registered in `scope(RequestScope) { scoped { } }`; `ActivatorUtilities` is replaced by an internal activator (primary constructor, parameters resolved by class) | See [specs/dependency-injection.md](specs/dependency-injection.md) |
| Tests | `IoxideFilesTests` are not ported | They only run on the Ioxide engine upstream |
| ReverseProxy | The JDK `HttpClient` replaces `HttpClient`/`SocketsHttpHandler`: `adjustHandler` takes `(HttpClient.Builder) -> Unit`, `adjustClient` runs on the built client; the read timeout is applied per request (`readTimeout` parameter on `ReverseProxyProvider`/`HttpProxy`) | The JDK client is immutable and has no handler type |
| ReverseProxy | `Expect`, `Transfer-Encoding` and `Content-Length` request headers are not copied (the body framing is derived from the body publisher); the JDK client adds a `User-Agent` if the client sent none | Restricted headers of the JDK client |
| ReverseProxy | `WebsocketTunnelContent` takes the request as a second argument; `DuplexPipe` wraps a socket's streams; the tunnel ends when either direction ends | The client side of the tunnel is read from `request.upgrade()`; no System.IO.Pipelines |
| ReverseProxy | Upstream status codes unknown to `ResponseStatus` are answered with 502; `Content-*` request headers are matched case-insensitively | Avoids invalid responses / 500s for lower-case headers |
