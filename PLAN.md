# Implementation Plan

This plan describes how the initial port of GenHTTP to Kotlin is built. Progress is tracked in [STATUS.md](STATUS.md),
the translation rules are in [PORTING.md](PORTING.md). A fresh session should read STATUS.md first, then this file.

Upstream baseline for the initial port:

- GenHTTP `3c25d473e57d4ae2692214089536da2f5be289e3` (version 11.1.0)
- Glyph11 `f5d2cc12a2a59f12a0c211ad7e5b4cdfd97c671f`

Local checkouts are expected at `../GenHTTP` and `../Glyph11`.

## Stages

Each stage ends with: build green, tests green, STATUS.md updated, a commit (or several), and a push. Stages are
ordered along the dependency graph of GenHTTP (`Modules → API ← Engine`, engine depends on IO/ErrorHandling).

| #  | Stage                         | Scope |
|----|-------------------------------|-------|
| 0  | Scaffolding                   | Gradle 9 build (Kotlin DSL, version catalog, `build-logic` conventions, explicit API mode, Kover, ABI dumps, publishing), docs (README, CONTRIBUTING, PORTING, PLAN, STATUS, SYNC, CLAUDE), `upstream.properties`, CI skeleton, `tools/` scripts |
| 1  | Glyph11                       | Port of the managed Glyph11 parser (UltraHardened + Flexible parsers, chunked bodies, validation, limits) and all of its unit tests |
| 2  | API                           | Complete `API/General` port incl. `MemoryView` base type, KDoc |
| 3  | Engine core                   | Modules needed by the engine (IO, Pages, Conversion, ErrorHandling, Redirects, Layouting), `Engine/Shared`, `Engine/Internal` (NIO + virtual threads + coroutines, TLS via SSLEngine), smoke test |
| 4  | Testing & engine acceptance   | `Testing/Testing` (`TestHost`, extensions), acceptance test infrastructure (`@MultiEngineTest`, `AssertX`, utilities), Practices + Compression + Security + ClientCaching + Protobuf, port of all Engine/Api/IO/Layouting/Redirects/ErrorHandling/Conversion/Compression/Security/ClientCaching/Protobuf acceptance tests |
| 5  | Reflection frameworks         | Reflection, Functional (Inline), Webservices, Controllers + acceptance tests |
| 6  | API documentation             | OpenApi, ApiBrowsing + acceptance tests |
| 7  | Cross-cutting modules         | Authentication, DependencyInjection (Koin), Inspection, I18n + acceptance tests |
| 8  | Content modules               | Files, StaticWebsites, SinglePageApplications, DirectoryBrowsing, Archives, VirtualHosting, LoadBalancing + acceptance tests |
| 9  | Connection modules            | ReverseProxy, Websockets, ServerSentEvents + acceptance tests |
| 10 | Distribution                  | `genhttp-core` / `genhttp-full` aggregates, Playground, Benchmarks (JMH), release workflow, README/CONTRIBUTING final |
| 11 | Coverage & platforms          | Coverage ≥ 90 % (Kover verification rule), CI matrix green on Linux/Windows/macOS x64+arm64 |
| 12 | API parity pass               | Full comparison of the public surface (API + all modules + testing) against upstream with `tools/api-parity`, fix gaps |
| 13 | Sync readiness                | Final pass on SYNC.md/CLAUDE.md, scheduled upstream-watch workflow |

## Work split and validation of sub-agents

Implementation plans are written by the lead session; execution of well-specified work may be delegated to
sub-agents. Sub-agents are expected to take shortcuts, so their output is validated mechanically and by review:

1. **Tests first, by a different agent.** For a module, one agent ports the upstream acceptance tests *only*
   (faithful 1:1 translation, no implementation). A second agent implements the module and is **not allowed to touch
   test files**. The lead verifies with `git diff --stat` that the implementation commit does not modify tests.
2. **Test parity check.** `tools/test-parity` compares every upstream `[TestMethod]` with the ported Kotlin tests
   (class + method name). Missing or extra tests fail the check. It also flags `@Disabled`, empty test bodies,
   tests without assertions and `assumeTrue`.
3. **API parity check.** `tools/api-parity` extracts the public surface of the upstream C# project and compares it with
   the Kotlin ABI dump (`api/*.api`) using the naming rules of PORTING.md. Unmatched members must either be ported or be
   listed as an accepted deviation in `tools/api-parity/allowlist.txt` with a reason.
4. **Review.** The lead reads every diff of production code and spot-checks test translations against upstream
   (expected values, status codes, header names must not change).
5. **Coverage.** Kover reports per module; a module that drops below its upstream-like coverage is investigated.

## Engine architecture (summary)

- `ServerSocketChannel` accept loop per endpoint on a virtual thread; each connection runs as a coroutine on a
  virtual-thread dispatcher. Reads/writes use blocking NIO `SocketChannel`s (virtual threads park, the JDK poller does
  the async work), header read timeouts via `withTimeout` + `runInterruptible`.
- TLS via an `SSLEngine`-based `ByteChannel`; certificate selection per SNI through a custom `X509ExtendedKeyManager`
  backed by `CertificateProvider`.
- Requests are parsed with glyph11-kt over a contiguous, growable connection buffer (the `PipeReader` equivalent).
  Request objects are reused per connection like upstream.
- Responses are serialized into a buffered NIO writer (`WritableByteChannel` sink + `OutputStream` adapter), chunked
  encoding when the length is unknown, `Server` / `Date` header caching like upstream.

## Publishing

- Maven Central (Central Portal) via the `com.vanniktech.maven.publish` plugin, signed artifacts, group `org.genhttp`.
- Versions follow the upstream GenHTTP version plus a port revision: `<upstream>.<revision>` (see SYNC.md).
