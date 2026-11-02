# AGENTS.md

Guidance for coding agents working on genhttp-kt, the Kotlin port of [GenHTTP](https://github.com/Kaliumhexacyanoferrat/GenHTTP).
Humans should read [CONTRIBUTING.md](CONTRIBUTING.md) first. It defines the policy (Definition of Done, architecture
principles, legal terms), this file explains how to meet it. If the two disagree, CONTRIBUTING.md wins; please flag the
conflict.

**Read first:** [docs/PORTING.md](docs/PORTING.md) (how C# maps to Kotlin — mandatory), [docs/SYNC.md](docs/SYNC.md)
(how upstream changes are ported). [upstream.properties](upstream.properties) records the last ported upstream commits,
[docs/PORT.md](docs/PORT.md) gives an overview of the port.

## Why this project exists

genhttp-kt is an automated port of GenHTTP to Kotlin, created by Claude as a proof of concept. It answers the question
whether a framework can be kept in sync across ecosystems by agents: GenHTTP stays the source of truth, genhttp-kt
follows it commit by commit. The port keeps GenHTTP's public API and behavior 1:1 while being idiomatic, modern Kotlin
(coroutines, NIO, virtual threads). See the philosophy section of docs/PORTING.md.

## Commands

```bash
./gradlew build                          # compile, test, ABI check, coverage verification
./gradlew test                           # all tests
./gradlew :testing-acceptance:test --tests "org.genhttp.testing.acceptance.engine.BasicTests"
./gradlew updateKotlinAbi                # refresh api/*.api dumps after intended public API changes
./gradlew koverHtmlReport                # coverage report (build/reports/kover)
./gradlew :playground:run                # demo server on port 8080
python tools/test_parity.py              # compare ported tests with upstream (needs ../GenHTTP, ../Glyph11)
python tools/api_parity.py --verbose     # compare the public API with upstream (exclusions: tools/api_parity_exclusions.txt)
./gradlew :benchmarks:mainBenchmark     # JMH benchmarks of the request pipeline (:benchmarks:mainBaselineBenchmark for one)
```

Upstream checkouts are expected next to this repository: `../GenHTTP` and `../Glyph11`.

## Architecture

Same layering as GenHTTP, enforced by Gradle dependencies:

- `API/General` — public interfaces only (depends on kotlinx-coroutines and SLF4J API, nothing else).
- `Engine/Shared`, `Engine/Internal` — the HTTP server. Must not depend on modules except those upstream uses
  (IO, ErrorHandling and their dependencies).
- `Modules/*` — content modules. Never depend on `Engine/*`.
- `Glyph11` — the HTTP/1.1 parser used by the engine (port of dotnet-web-stack/Glyph11).
- `Testing/Testing` — public test helpers (`TestHost`), `Testing/Acceptance` — the acceptance test suite.

## Request Lifetime (where subtle bugs come from)

- `Request` instances and their memory are **reused** for the next request on the same connection, and connection
  state is pooled. Never store a request, or anything read from it, beyond the `handle` call.
- Header, path and query values are `ByteString` views into the read buffer of the connection. Reading the body can
  invalidate them. If a header value is needed after the body may have been consumed (e.g. after
  `content.handle(request)` in a concern), capture it first and call `.preAllocate(request)`.
- `getBody()` may be called once. `HeaderAccess.Release` makes later header access throw. Use it only when nothing
  downstream needs headers.
- `upgrade()` hands over the connection for protocol upgrades (websockets). After that, the handler owns the
  connection and does its own flushing.

## Performance

The request path is designed to be allocation-free like upstream, and both engines are benchmarked publicly
([HTTP Arena](https://www.http-arena.com/)). The hot path is anything that runs per request: the engines,
`Engine/Shared`, the protocol types of the API, the concerns added by `defaults()`, layout routing, the
Reflection/Webservices/Functional invocation path and file serving. Builders, `build()` and `prepare()` run once.

- No capturing lambdas, collection pipelines (`map`/`filter`) or iterator allocations on the hot path.
- Do not convert `ByteString` to `String` to compare or look things up. Use `KnownHeaders.*` and other precomputed
  `ByteString` constants.
- Compute once (constructor, `build()`, `prepare()`), and be lazy: don't parse what the request does not need.
- Measure with `Benchmarks/` (JMH via kotlinx-benchmark) and report time and allocations per operation before and
  after; don't claim effects below the noise of repeated runs.

## Security

genhttp-kt terminates connections from the internet. Treat every byte of a request as hostile, including path, query,
headers (`Host`, `X-Forwarded-For`, `Forwarded`), cookies and body.

- **Validate after decoding.** Percent-decoding can introduce `/`, `\`, `..` or rooted paths (`%2f`, `%5c`). Check
  the decoded value, resolve it and verify it stays inside its root before touching the file system or an upstream URL.
- **Encode output.** HTML-escape anything request- or file-derived that ends up in a page (listings, error pages,
  templates).
- **Don't trust client-supplied routing headers** for security decisions.
- **Secure defaults.** Unsafe behavior is opt-in and named as such.
- **Bound everything the client controls**: body size, nesting depth (a stack overflow kills the thread), decompression
  output, collection sizes, waiting time.
- **No unsafe deserialization** (no polymorphic type handling driven by request data, no loading classes based on it).
- Never return stack traces or internal paths outside development mode.
- Do not disclose a suspected vulnerability in a public issue, PR or commit message. Follow [SECURITY.md](SECURITY.md).

## HTTP Correctness and Engine Parity

- The same handler must behave identically on the internal and the Netty engine (both run every `@MultiEngineTest`).
- Header names are case-insensitive, repeated headers must all survive, `Content-Length` must equal the bytes written,
  `101`, `204` and `304` responses carry no body, `HEAD` gets headers without a body.
- **Root-cause every failure.** Before calling a failure an "engine limitation" or "flaky", check whether other tests
  of the same feature pass on that engine. If they do, there is a bug to find.

## Rules

- **Follow docs/PORTING.md.** Naming (no `I` prefix, no `Async` suffix), type mapping, fluent builders, reflection approach.
- **API parity is the top priority.** Every public type and member of upstream exists here with the translated name,
  same parameters, defaults and nullability. When unsure, read the upstream source.
- **Tests mirror upstream 1:1** (same class and method names, same assertions). Never weaken, skip or delete a test to
  make code pass. `@Disabled` and assumptions are forbidden. Platform-caused differences go to the deviation log (`docs/DEVIATIONS.md`).
- **Explicit API mode** is on: public declarations need explicit visibility and KDoc.
- **Warnings are errors.**
- Implementations are `internal` unless upstream makes them `public`. Builders and handler classes that upstream
  exposes publicly stay public.
- Keep `api/*.api` dumps in sync (`./gradlew updateKotlinAbi`) and review the diff — it is the public surface.
- Every deviation from upstream in spirit or behavior is added to the deviation log ([docs/DEVIATIONS.md](docs/DEVIATIONS.md)).
- Commits reference upstream commits with `Upstream-Commit:` trailers when porting upstream changes (see docs/SYNC.md).

## Delegating to sub-agents

When the lead session delegates module work:

1. One agent ports the upstream tests only; another implements the module and must not modify test files.
2. The lead verifies `git diff --stat` (no test changes in implementation work), runs `python tools/test_parity.py`,
   the full build, and reviews production diffs.
3. Agents get the upstream file list, the relevant docs/PORTING.md rules and the exact target paths/packages.
