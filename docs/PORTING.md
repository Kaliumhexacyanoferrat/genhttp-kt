# Porting Rulebook

This document defines **how** GenHTTP (C#) is translated into genhttp-kt (Kotlin). Every agent and contributor
working on the port must follow it. If a rule does not cover a situation, decide in the spirit of the
[philosophy](#philosophy) and add the decision to the [deviation log](#deviation-log).

## Philosophy

genhttp-kt is "GenHTTP, but on modern Kotlin". Two goals pull in different directions and both matter:

1. **API parity.** The public surface of GenHTTP is kept 1:1: every type, property, method, parameter, default value
   and concept of the API project and of every module (handlers, concerns, builders, request/response, sinks,
   bodies, registries, method handlers, interceptors, testing, ...). Code written against GenHTTP (an inline web
   service, a static website, an SPA, a controller) must read *functionally identical* in genhttp-kt.
   Nothing is skipped. If a concept cannot be ported literally, it is ported in spirit.
2. **Kotlin-native.** The port must not feel artificial or transliterated. Platform specifics (`Stream`, `Socket`,
   `ValueTask`, `Assembly`, `HttpClient`, csproj, NuGet, ...) are replaced with the recommended, modern Kotlin/JVM
   equivalent, applied consistently everywhere.

When both goals clash, keep the *concept and behavior* of GenHTTP and express it the way a Kotlin developer would
expect. The philosophy wins over any single example in this document.

## Project layout

The folder structure mirrors GenHTTP so that upstream paths map mechanically:

| GenHTTP                                   | genhttp-kt (Gradle project)                    | Kotlin package                     |
|-------------------------------------------|------------------------------------------------|------------------------------------|
| `API/General`                             | `API/General` (`:api`)                         | `org.genhttp.api`                  |
| `Engine/Shared`                           | `Engine/Shared` (`:engine-shared`)             | `org.genhttp.engine.shared`        |
| `Engine/Internal`                         | `Engine/Internal` (`:engine-internal`)         | `org.genhttp.engine.internal`      |
| `Modules/<Name>`                          | `Modules/<Name>` (`:modules-<name>`)           | `org.genhttp.modules.<name>`       |
| `Testing/Testing`                         | `Testing/Testing` (`:testing`)                 | `org.genhttp.testing`              |
| `Testing/Acceptance`                      | `Testing/Acceptance` (`:testing-acceptance`)   | `org.genhttp.testing.acceptance`   |
| `Playground`                              | `Playground` (`:playground`)                   | `org.genhttp.playground`           |
| `Benchmarks`                              | `Benchmarks` (`:benchmarks`)                   | `org.genhttp.benchmarks`           |
| `Packages/GenHTTP.Core.nuspec` / `.Full`  | `Packages/Core`, `Packages/Full`               | (aggregate artifacts)              |
| Glyph11 `src/Glyph11` (separate repo)     | `Glyph11` (`:glyph11`)                         | `org.genhttp.glyph11`              |

Within a project, sources live in `src/main/kotlin` and use Kotlin's recommended layout that **omits the common root
package**: `Modules/Layouting/Provider/LayoutBuilder.cs` becomes
`Modules/Layouting/src/main/kotlin/provider/LayoutBuilder.kt` (package `org.genhttp.modules.layouting.provider`).
Embedded resources move to `src/main/resources/<package path>/` (see [Classpath](#assemblies--classpath)).
Multi-word module names use kebab-case for Gradle (`:modules-single-page-applications`) and concatenated lowercase for
packages (`org.genhttp.modules.singlepageapplications`).

Maven coordinates: group `org.genhttp`, artifacts `genhttp-api`, `genhttp-engine-internal`, `genhttp-modules-layouting`,
`genhttp-testing`, `genhttp-core`, `genhttp-full`, `glyph11-kt`, ...

## Naming

| C#                                              | Kotlin                                                                 |
|-------------------------------------------------|------------------------------------------------------------------------|
| `IHandler`, `IRequest`, `IResponseBuilder`, ... | **No `I` prefix**: `Handler`, `Request`, `ResponseBuilder`, ...         |
| `PascalCase` methods / properties               | `camelCase` (`Respond()` → `respond()`, `Header` → `header`)           |
| `XyzAsync()` returning `ValueTask`              | `suspend fun xyz()` (the `Async` suffix is dropped)                    |
| `Attribute` suffix (`ResourceMethodAttribute`)  | annotation class without suffix (`@ResourceMethod`)                    |
| static well-known instances (`ContentType.TextHtml`, `RequestMethod.Get`, `KnownHeaders.Host`) | keep `UpperCamelCase` (Compose style: `ContentType.TextHtml`) |
| enum members (`ResponseStatus.Ok`)              | keep `UpperCamelCase` (`ResponseStatus.Ok`)                            |
| `const` (`BufferSize.Read`)                     | `const val` keeps the name (`BufferSize.Read`)                         |

### Resolving clashes caused by dropping the `I` prefix

Dropping the prefix makes some interfaces collide with GenHTTP's static factory classes or implementations. Rules:

1. **Factory clashes are solved with companion objects.** The API interface declares an empty `companion object`, and
   the module adds its factory functions as companion extensions. The call site stays identical to GenHTTP:
   ```kotlin
   // API/General
   interface Resource { ...; companion object }
   // Modules/IO
   fun Resource.Companion.fromString(data: String): StringResourceBuilder = ...
   // user code (GenHTTP: Resource.FromString("Hello"))
   Resource.fromString("Hello")
   ```
   Affected: `Handler.from`, `Concern.from`, `Resource.from*`, `ResourceTree.from*`, `CertificateProvider.from`, ...
2. **Implementation clashes** (an implementation class named like an API interface) rename the implementation with a
   descriptive prefix, never an `Impl` suffix. The engine uses `Http*` (`HttpRequest`, `HttpResponse`,
   `HttpResponseBuilder`, `HttpRequestTarget`, ...). Elsewhere pick a prefix that describes the variant
   (`InlineMethodConfiguration` for Functional's `MethodConfiguration` record).
3. **Annotation clashes**: when dropping the `Attribute` suffix makes an annotation collide with an API type, the
   annotation gets an `Api` prefix (`ResponseAttribute` → `@ApiResponse`, as known from Swagger).
4. Record each rename in the [deviation log](#deviation-log).

### Files

One Kotlin file per upstream C# file, named after the main declaration without the `I` prefix. Files that only
contain extension functions (C# static extension classes) carry `@file:JvmName("<C# class name>")`, e.g.
`RequestHeadersCookies.kt` with `@file:JvmName("CookieHeaderExtensions")`, so the ABI dump mirrors upstream names.

## Type mapping

| C# / .NET                                      | Kotlin / JVM                                                                 |
|------------------------------------------------|-------------------------------------------------------------------------------|
| `ValueTask`, `Task`                            | `suspend fun ...` returning `Unit`                                            |
| `ValueTask<T>`, `Task<T>`                      | `suspend fun ...: T`                                                          |
| `Func<A, ValueTask<R>>`                        | `suspend (A) -> R`                                                            |
| `Func<A, R>` / `Action<A>`                     | `(A) -> R` / `(A) -> Unit`                                                    |
| `Action<IResponseBuilder>` (modifications)     | `ResponseBuilder.() -> Unit` (lambda with receiver)                           |
| `Delegate` (inline handlers)                   | suspend lambdas + `KFunction` references (see [Reflection](#reflection))      |
| `Stream` (reading)                             | `java.io.InputStream`                                                         |
| `Stream` (writing)                             | `java.io.OutputStream`                                                        |
| `IBufferWriter<byte>`                          | `java.nio.channels.WritableByteChannel`                                       |
| `PipeReader` (`IRequest.Upgrade()`)            | `java.io.InputStream` (buffered remainder of the connection)                  |
| `ReadOnlyMemory<byte>`, `Memory<byte>`, `Span` | `java.nio.ByteBuffer` — content is `position..limit`, see below               |
| `byte[]`                                       | `ByteArray`                                                                   |
| `ulong` / `long` lengths and checksums         | `Long`                                                                        |
| `ushort` port                                  | `Int` (validated)                                                             |
| `DateTime`, `DateTimeOffset`                   | `java.time.Instant`                                                           |
| `TimeSpan`                                     | `kotlin.time.Duration`                                                        |
| `DateOnly` / `TimeOnly`                        | `java.time.LocalDate` / `java.time.LocalTime`                                 |
| `Guid`                                         | `java.util.UUID`                                                              |
| `Uri`                                          | `java.net.URI`                                                                |
| `IPAddress`                                    | `java.net.InetAddress`                                                        |
| `CultureInfo`                                  | `java.util.Locale`                                                            |
| `Type`                                         | `KType` (when nullability/generics matter) or `KClass<*>`                     |
| `MethodInfo`, `ParameterInfo`                  | `KFunction<*>`, `KParameter`                                                  |
| `Assembly`                                     | the classpath (`ClassLoader`, `KClass` anchors)                               |
| `FileInfo`, `DirectoryInfo`, paths             | `java.nio.file.Path`                                                          |
| `X509Certificate2` (with private key)          | `java.security.KeyStore.PrivateKeyEntry`                                      |
| `X509Certificate`, `X509Chain`                 | `java.security.cert.X509Certificate`, `List<X509Certificate>`                 |
| `SslProtocols`, `SslPolicyErrors`, `X509RevocationMode` | small enums in `org.genhttp.api.infrastructure` (no JDK equivalent)  |
| `ILoggerFactory`, `ILogger`                    | SLF4J `org.slf4j.ILoggerFactory`, `org.slf4j.Logger`                          |
| `HttpClient`                                   | `java.net.http.HttpClient`                                                    |
| `Regex`                                        | `kotlin.text.Regex`                                                           |
| `record`                                       | `data class` (or `class` when identity semantics are needed)                  |
| `readonly struct`                              | immutable `class` / `data class`                                              |
| `[Flags] enum`                                 | `enum class` + `Set<E>` / `EnumSet`                                           |
| `IDisposable` / `IAsyncDisposable`             | `AutoCloseable` / `suspend fun close()`                                       |
| `TryGet(key, out value)`                       | function returning a nullable value (`get(key): T?`) — keep the name `tryGet` |
| `params T[]`                                   | `vararg`                                                                      |
| `IEnumerable<T>`, `IReadOnlyList<T>`           | `Iterable<T>`, `List<T>`                                                      |
| `Dictionary`, `HashSet`, `List`                | `MutableMap`, `MutableSet`, `MutableList` (read-only interfaces in signatures)|
| `KeyValuePair<K, V>`                           | `Pair<K, V>`                                                                  |
| extension methods                              | extension functions                                                           |
| XML doc comments                               | KDoc on every public declaration                                              |

### Byte buffers (`ReadOnlyMemory<byte>`)

`ByteBuffer` is the JVM's standard memory view and is used wherever GenHTTP uses `ReadOnlyMemory<byte>`. Conventions:

- Buffers handed out by the API are **read-only** views whose content is `position until limit`.
- Consumers never mutate the position/limit of a buffer they did not create. Use absolute access
  (`get(index)`) or `duplicate()` first. APIs that expose stored buffers (e.g. `MemoryView.bytes`) return a fresh
  `duplicate()`.
- Engine-internal hot paths may access backing arrays directly (`hasArray()`), never public API consumers.

### MemoryView types

GenHTTP generates `ByteString`, `ContentType`, `RequestMethod`, `HttpProtocol`, `PathSegment` and `AlgorithmName` via
the `[MemoryView]` source generator. genhttp-kt replaces the generator with an abstract base class
`org.genhttp.api.MemoryView` that implements the generated members once (ASCII construction from `String`,
case-insensitive hash and equality, `bytes`, `toString()`); the concrete types are small subclasses. Equality only
holds between instances of the same type, like in GenHTTP. Comparison with raw memory uses `equals(ByteBuffer)` and the
infix helpers instead of operators.

## Asynchrony and I/O

- **Coroutines everywhere.** Every `ValueTask`/`Task` API is a `suspend` function. There is exactly one async model.
- **NIO for bytes.** The engine reads and writes through NIO channels into `ByteBuffer`s.
- **Virtual threads under the hood.** Each connection runs as a coroutine on a dispatcher backed by virtual threads
  (`Executors.newVirtualThreadPerTaskExecutor()`). This makes the blocking-shaped JVM stream types that the API must
  expose (`InputStream` request bodies, `OutputStream` sinks, serializers, compressors) safe to use from suspend
  handlers: a blocking call only parks a virtual thread. Suspending handlers (`delay`, channels, flows) release it.
- Handlers must not assume a specific dispatcher. Blocking calls inside handlers are allowed (they run on virtual
  threads), long CPU work should use `Dispatchers.Default`.

## Builders

GenHTTP's fluent builders are kept as they are (decided by the maintainer): factories return builders, builder
functions return the builder, `build()` creates the instance. Fluent builders are idiomatic in Kotlin as well, and
keeping them makes code read exactly like GenHTTP. No DSL configuration lambdas or `@DslMarker` are added; users may
still use Kotlin's `apply { }` on any builder.

```kotlin
// GenHTTP:  var app = Layout.Create().Add("api", api).Index(index);
val app = Layout.create()
    .add("api", api)
    .index(index)

Host.create()
    .handler(app)
    .defaults()
    .run()
```

## Reflection

The Reflection module analyzes signatures with **kotlin-reflect** and invokes through it. There is no code generation.

- **Methods of classes** (webservices, controllers): `KClass.memberFunctions`, annotations, `KParameter` (name, `KType`
  incl. nullability, `isOptional`), suspend functions are first-class (`callSuspendBy`).
- **Inline handlers**: GenHTTP's `Get((int a, int b) => a + b)` becomes `get { a: Int, b: Int -> a + b }`. Kotlin 2
  compiles plain lambdas through `invokedynamic` without metadata, but **suspend lambdas are compiled to classes that
  keep full metadata** (parameter names and nullable types via `reflect()`). Inline methods therefore take
  `suspend` lambdas (arity 0 as member, arity 1..N as extension overloads so that `{ "x" }` resolves to arity 0).
- Overloads for lambdas with parameters are extension functions, so code using them imports the module package
  (`import org.genhttp.modules.functional.*`, the counterpart of `using GenHTTP.Modules.Functional;`). Without the
  import, the compiler only sees the member overloads and reports a type mismatch.
- Kotlin does not allow annotations or default values on lambda parameters. Inline handlers that need them
  (`[FromBody]`, `[Summary]`, `int x = 42`) use **function references** (`post(::handler)`), which carry everything.
- Default values cannot be read via kotlin-reflect. Optional parameters are omitted from the call so that Kotlin's own
  default applies (`callBy`); `OperationArgument` exposes `isOptional` instead of the default value.
- `ExecutionMode` is kept: `Auto` uses a precomputed positional invocation fast path, `Reflection` always uses
  `callSuspendBy`. Both are tested by `@MultiEngineFrameworkTest`.

## Assemblies → classpath

`Resource.FromAssembly(...)` / `ResourceTree.FromAssembly(...)` become classpath based and behave like a JVM developer
expects classpath resources to behave:

- `Resource.fromClasspath("web/index.html")` resolves an absolute classpath path (thread context class loader by
  default, overloads accept a `ClassLoader`).
- `Resource.fromClasspath(MyType::class, "file.txt")` resolves relative to the package of `MyType`
  (`Class.getResource` semantics).
- `ResourceTree.fromClasspath("web")` enumerates a classpath directory across **all** classpath roots (directories and
  JARs), so it works the same in the IDE and in a packaged application.
- Internal templates of modules live in `src/main/resources/org/genhttp/modules/<module>/...`.

## Third-party libraries

| Purpose (GenHTTP library)                  | genhttp-kt                                         |
|--------------------------------------------|----------------------------------------------------|
| Logging (Microsoft.Extensions.Logging)     | SLF4J 2 API, built-in console fallback             |
| JSON (System.Text.Json)                    | Jackson 3 + Kotlin module                          |
| XML (XmlSerializer)                        | Jackson 3 XML                                      |
| YAML (YamlDotNet)                          | Jackson 3 YAML                                     |
| Protobuf (protobuf-net, code first)        | kotlinx-serialization-protobuf (code first)        |
| Templates (Cottle)                         | Pebble                                             |
| OpenAPI (NSwag)                            | swagger-core / swagger-models (io.swagger.v3)      |
| JWT / OIDC (System.IdentityModel)          | Nimbus JOSE + JWT                                  |
| Archives (SharpCompress)                   | Apache Commons Compress                            |
| Brotli / Zstd (BCL)                        | Brotli4j / zstd-jni (native, multi-platform)       |
| Dependency injection (MS DI)               | Koin                                               |
| HTTP client (HttpClient)                   | `java.net.http.HttpClient`                         |
| Tests (MSTest)                             | JUnit 6 (Jupiter) + kotlin.test                    |
| Coverage (coverlet + Sonar)                | Kover (JaCoCo XML for Sonar)                       |

## Tests

- Acceptance tests (`Testing/Acceptance`) are the primary test suite, exactly like upstream. Unit tests only where
  upstream has them (Glyph11) or where acceptance tests cannot reach code.
- Each upstream test class becomes a Kotlin class with the same name in the mirrored package; each test method keeps
  its name in camelCase (`TestBuilder` → `testBuilder`). `tools/test_parity.py` verifies this mechanically.
- `[MultiEngineTest]` → `@MultiEngineTest` (parameterized over `ServerEngine`), `[MultiEngineFrameworkTest]` →
  `@MultiEngineFrameworkTest` (engine × `ExecutionMode`).
- Tests are never weakened to make an implementation pass. A test that cannot be ported 1:1 for a platform reason is
  ported as closely as possible and listed in the deviation log with the reason. `@Disabled` is not allowed.
- Tests without an upstream counterpart (Kotlin-specific code, coverage of JVM-only paths) live in the `additional`
  package (`Testing/Acceptance/src/test/kotlin/additional/<area>/`), never in the mirrored files. The parity tool
  ignores this package, so the mirror of upstream stays exact and syncs stay mechanical.

## Deviation log

Every deviation from upstream (API or behavior) is recorded in [DEVIATIONS.md](DEVIATIONS.md). Search it
(`grep -n "<Module>" docs/DEVIATIONS.md`) instead of reading it as a whole.
