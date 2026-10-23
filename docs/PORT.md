# About the Port

genhttp-kt is an automated port of [GenHTTP](https://github.com/Kaliumhexacyanoferrat/GenHTTP) to Kotlin, created and
maintained by [Claude](https://claude.ai) as a proof of concept. It answers the question whether a framework can be
kept in sync across ecosystems by agents: GenHTTP stays the source of truth, genhttp-kt follows it commit by commit.

The port pursues two goals (see the [philosophy](PORTING.md#philosophy) of the porting rulebook):

- **API parity.** Every public type and member of GenHTTP exists in genhttp-kt with the translated name, the same
  parameters, defaults and nullability. Code written against GenHTTP reads functionally identical in Kotlin.
- **Kotlin-native.** Platform specifics are replaced with their modern Kotlin/JVM equivalents: coroutines instead of
  `ValueTask`, NIO and virtual threads instead of .NET sockets and streams, the classpath instead of assemblies.

## Coming from GenHTTP

The [documentation of genhttp-kt](https://genhttp-kt.genhttp.run/documentation/) is the
[GenHTTP documentation](https://genhttp.org/documentation/) ported to Kotlin, page by page and at the same addresses.
C# samples translate mechanically (see also
[Coming from GenHTTP](https://genhttp-kt.genhttp.run/documentation/port/coming-from-genhttp/) on the website):

| GenHTTP (C#)                                   | genhttp-kt (Kotlin)                                 |
|------------------------------------------------|-----------------------------------------------------|
| `IHandler`, `IRequest`, `IResponseBuilder`     | `Handler`, `Request`, `ResponseBuilder`             |
| `ValueTask<IResponse?> HandleAsync(...)`       | `suspend fun handle(...): Response?`                |
| `Layout.Create().Add("api", api)`              | `Layout.create().add("api", api)`                   |
| `.Get((int a, int b) => a + b)`                | `get { a: Int, b: Int -> a + b }`                   |
| `[ResourceMethod(RequestMethod.Post)]`         | `@ResourceMethod(Method.Post)`                      |
| `Resource.FromAssembly("index.html")`          | `Resource.fromClasspath("index.html")`              |
| `Stream`                                       | `InputStream` / `OutputStream`                      |
| `using GenHTTP.Modules.Functional;`            | `import org.genhttp.modules.functional.*`           |

Inline handlers take suspend lambdas, which keep the parameter names and types needed to map requests. Kotlin does not
allow annotations or default values on lambda parameters, so handlers that need them (`[FromBody]`, `int x = 42`) use
function references instead (`post(::handler)`).

The [porting rulebook](PORTING.md) contains the complete mapping, the [deviation log](DEVIATIONS.md) lists every place
where genhttp-kt intentionally differs from GenHTTP.

## Engine

genhttp-kt ships with two HTTP/1.1 engines: the internal engine (`Engine/Internal`, a port of GenHTTP's internal
engine) and the Netty engine (`Engine/Netty`, the counterpart of GenHTTP's ioxide engine). Kestrel is specific to .NET
and therefore not ported, ioxide is replaced by Netty. Modules are engine-independent, exactly like in GenHTTP, and the
acceptance tests run on both engines.

The internal engine:

- Every endpoint accepts connections on a virtual thread, every connection runs as a coroutine on a dispatcher backed
  by virtual threads. Reads and writes use blocking NIO channels: a blocking call only parks a virtual thread, the JDK
  does the asynchronous work. This makes the stream types of the API safe to use from suspending handlers.
- TLS is implemented on top of `SSLEngine`, certificates are selected per SNI host name through the
  `CertificateProvider` of the endpoint.
- Requests are parsed by `glyph11-kt`, a port of the hardened [Glyph11](https://github.com/dotnet-web-stack/Glyph11)
  parser, over a contiguous connection buffer. Request objects are reused per connection like upstream.
- Responses are serialized into a buffered NIO writer, using chunked encoding if the content length is unknown.

The Netty engine (see [the design](specs/netty-engine.md)):

- One Netty event loop per core (epoll if available, io_uring or NIO on request). With the native transports, every
  loop binds its own socket (`SO_REUSEPORT`), so accepting and serving connections is spread over the loops.
- Every connection runs as a coroutine on the loop that accepted it. The coroutine suspends until the loop delivers
  data, data received by the loop resumes it inline, coroutines that resume on other threads are handed back to their
  loop, timers stay on the loop. Handlers must not block (like with ioxide), suspending is fine.
- Request bodies are buffered completely before the handler runs. Responses are written into buffers of Netty's
  allocator without copying, pipelined responses are sent with a single flush. Websockets run on the loop as well,
  reading the upgraded connection through the `SuspendingInput` API addition. The state of connections is pooled per
  loop.
- TLS is terminated by Netty's `SniHandler` with OpenSSL (BoringSSL) or the JDK provider, the certificate is selected
  per SNI host name through the `CertificateProvider` of the endpoint.
- Parsing (glyph11-kt), the request and response types and the serialization are shared with the internal engine
  (`Engine/Shared`).

## Keeping in Sync

[`upstream.properties`](../upstream.properties) records the last GenHTTP and Glyph11 commits that are fully reflected
in this repository. A daily workflow checks both repositories and maintains an issue that lists the commits that have
not been ported yet. An agent then ports them following the [sync procedure](SYNC.md): production code and tests
together, one commit per upstream change, referenced with an `Upstream-Commit:` trailer. Changes that only affect
.NET-specific parts (Kestrel, the ASP.NET Core adapter, NuGet packaging) are not ported, but their intent is checked
against the Kotlin code. Changes to ioxide are ported to the Netty engine by intent.

## Verification

Faithfulness to upstream is checked mechanically on every build:

- **Tests mirror upstream 1:1.** Every upstream test exists with the same class and method name and the same
  assertions. `tools/test_parity.py` compares the test suites and flags disabled, empty or assertion-free tests.
  Tests without an upstream counterpart live in a separate `additional` package.
- **API parity.** `tools/api_parity.py` compares the public surface of GenHTTP with the Kotlin ABI dumps (`api/*.api`),
  including parameter counts and default values (`--signatures`). Intentional differences are listed in
  `tools/api_parity_exclusions.txt` and the deviation log.
- **ABI dumps.** Changes of the public API show up in the `api/*.api` files and are reviewed like code.
- **Coverage.** Line coverage must not drop below 90 % (Kover).
- **Platforms.** The build runs on Linux, Windows and macOS, each on x64 and arm64.

```sh
./gradlew build :koverVerify             # compile, tests, ABI check, coverage
python tools/test_parity.py              # needs ../GenHTTP and ../Glyph11
python tools/api_parity.py --verbose
python tools/api_parity.py --signatures
```

## Versioning

genhttp-kt versions are `<GenHTTP version>.<port revision>`, e.g. `11.1.0.0`, `11.1.0.1`, `11.2.0.0`. The first three
components are the GenHTTP version the port is based on, the fourth counts port-only releases (bug fixes in the port,
dependency updates) and resets with every new upstream version. See [SYNC.md](SYNC.md#versioning) for details.

## Documents

| Document                                 | Content                                                                         |
|------------------------------------------|---------------------------------------------------------------------------------|
| [PORTING.md](PORTING.md)                 | Porting rulebook: project layout, naming, type mapping, reflection, tests       |
| [SYNC.md](SYNC.md)                       | Procedure to port upstream changes, versioning                                  |
| [DEVIATIONS.md](DEVIATIONS.md)           | Every intentional difference from GenHTTP and its reason                        |
| [specs/](specs/)                         | Design specifications (Glyph11, Reflection, DependencyInjection), agent briefs  |
| [CONTRIBUTING.md](../CONTRIBUTING.md)    | How to contribute, definition of done                                           |
| [CLAUDE.md](../CLAUDE.md)                | Instructions for agents working on the port                                     |
