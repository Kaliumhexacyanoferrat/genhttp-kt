# Status

Updated at the end of every stage (see [PLAN.md](PLAN.md)). A fresh session resumes from the first stage that is not
done.

| #  | Stage                         | State       | Notes |
|----|-------------------------------|-------------|-------|
| 0  | Scaffolding                   | done        | Gradle 9.8.2, Kotlin 2.4.21, JDK 25, conventions in `build-logic`, test parity tool, CI skeleton |
| 1  | Glyph11                       | done        | 197/200 upstream tests (3 ReadOnlySequence tests excluded), all green |
| 2  | API                           | done        | Complete port, ABI dump in `API/General/api/api.api`; API tests are ported with the acceptance suite in stage 4 |
| 3  | Engine core                   | done        | Engine (NIO + virtual threads + coroutines, SSLEngine TLS), IO, Pages (Pebble), Conversion (Jackson 3), ErrorHandling, Redirects, Layouting |
| 4  | Testing & engine acceptance   | done        | 313 acceptance tests green (Engine, Api, IO, Layouting, Redirects, Conversion, ErrorHandling, Compression, Security, ClientCaching); a few IO/Layouting test files wait for unported modules (`pendingTests`) |
| 5  | Reflection frameworks         | done        | Reflection, Functional, Webservices, Controllers incl. tests (570 acceptance tests green in total) |
| 6  | API documentation             | done        | OpenApi (swagger-core), ApiBrowsing (Swagger UI, Redoc, Scalar) incl. tests |
| 7  | Cross-cutting modules         | done        | Authentication, I18n, Inspection, DependencyInjection (Koin, `docs/specs/dependency-injection.md`) incl. tests |
| 8  | Content modules               | done        | Files, Archives, DirectoryBrowsing, VirtualHosting, StaticWebsites, SinglePageApplications, LoadBalancing incl. tests |
| 9  | Connection modules            | done        | Websockets, ServerSentEvents, ReverseProxy incl. tests |
| 10 | Distribution                  | done        | `genhttp-core`/`genhttp-full`, Playground (`./gradlew :playground:run`), Benchmarks (kotlinx-benchmark/JMH, in-memory pipeline), release workflow |
| 11 | Coverage & platforms          | in progress | 821/821 upstream acceptance tests ported, line coverage 91 % (enforced by `:koverVerify` in CI); CI matrix to be confirmed on all platforms |
| 12 | API parity pass               | done        | `tools/api_parity.py` (names) and `--signatures` (parameter counts, defaults): 0 differences; intentional ones in `tools/api_parity_exclusions.txt`; CI gate |
| 13 | Sync readiness                | open        | |

## Decisions confirmed by the maintainer

- Native compression libraries are fine: Brotli4j and zstd-jni.
- Minimum JDK: 25 (LTS).
- Interfaces drop the `I` prefix (Kotlin style); clashes resolved as described in PORTING.md.
- Dependency injection integrates with Koin.

## Findings from experiments

- Kotlin 2.x compiles plain lambdas via `invokedynamic` → `reflect()` returns `null`, parameter names are lost.
- Suspend lambdas are still compiled to classes (`SuspendLambda`) → `reflect()` yields names and nullable types.
- Overloads `get(fn: suspend () -> R)` (member) + `get(fn: suspend (P1) -> R)` (extension) resolve `{ "x" }` to arity 0
  without ambiguity; with both as members the call is ambiguous.
- Lambda parameters cannot carry annotations (syntax error) and anonymous functions do not convert to suspend function
  types → annotated/defaulted inline parameters use function references (`::handler`).

## Next steps

- Confirm the CI matrix (Linux/Windows/macOS, x64/arm64) is green; fix platform issues.
- Stage 13: final pass on SYNC.md/CLAUDE.md and the upstream-watch workflow, then a first sync dry run against a newer
  upstream commit.
- Optional: raise branch coverage (currently lower than line coverage); additional tests go to the `additional`
  package (see PORTING.md).
