# Status

Updated at the end of every stage (see [PLAN.md](PLAN.md)). A fresh session resumes from the first stage that is not
done.

| #  | Stage                         | State       | Notes |
|----|-------------------------------|-------------|-------|
| 0  | Scaffolding                   | done        | Gradle 9.8.2, Kotlin 2.4.21, JDK 25, conventions in `build-logic`, test parity tool, CI skeleton |
| 1  | Glyph11                       | done        | 197/200 upstream tests (3 ReadOnlySequence tests excluded), all green |
| 2  | API                           | done        | Complete port, ABI dump in `API/General/api/api.api`; API tests are ported with the acceptance suite in stage 4 |
| 3  | Engine core                   | in progress | IO, Redirects, Layouting, ClientCaching, Security, Compression ported and committed. Engine Shared/Internal written (uncommitted until it compiles). Pages/Conversion/ErrorHandling were being ported by an agent when the session ended |
| 4  | Testing & engine acceptance   | in progress | Testing module (TestHost) and acceptance infra written; an agent was porting Engine/Api/Generators tests |
| 5  | Reflection frameworks         | open        | |
| 6  | API documentation             | open        | |
| 7  | Cross-cutting modules         | open        | |
| 8  | Content modules               | open        | |
| 9  | Connection modules            | open        | |
| 10 | Distribution                  | open        | |
| 11 | Coverage & platforms          | open        | |
| 12 | API parity pass               | open        | |
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

1. Check the state of `Modules/Pages`, `Modules/Conversion`, `Modules/ErrorHandling` (an agent was porting them,
   see `docs/specs/module-agent-brief.md`): build `:modules-pages`, `:modules-conversion`, `:modules-error-handling`,
   finish missing parts, `updateKotlinAbi`, commit.
2. Port Practices (`ServerHost.defaults(...)`, uses `strictTransport`, `compression`, `decompression`, `rangeSupport`,
   `clientCaching`, `secureUpgrade`, `preventSniffing`) and Protobuf (kotlinx-serialization-protobuf,
   `SerializationBuilder.addProtobuf()`).
3. Compile `:engine-shared`, `:engine-internal`, `:testing`; fix; commit the engine.
4. Check the ported Engine/Api/Generators acceptance tests (`Testing/Acceptance/src/test/kotlin`, written by an agent
   without compiling), run `python tools/test_parity.py --suite acceptance --filter Engine`, run the tests and fix the
   engine (never the tests, unless a translation error is verified against upstream).
5. Known issue: `Redirect.to("{sitemap}")` fails because `java.net.URI` rejects it (.NET accepts) — keep the location
   as a string and only detect absolute URIs.
6. Then continue with stage 4 module tests and stage 5 (see `docs/specs/reflection.md`).
