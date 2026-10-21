# Status

Updated at the end of every stage (see [PLAN.md](PLAN.md)). A fresh session resumes from the first stage that is not
done.

| #  | Stage                         | State       | Notes |
|----|-------------------------------|-------------|-------|
| 0  | Scaffolding                   | done        | Gradle 9.8.2, Kotlin 2.4.21, JDK 25, conventions in `build-logic`, test parity tool, CI skeleton |
| 1  | Glyph11                       | done        | 197/200 upstream tests (3 ReadOnlySequence tests excluded), all green |
| 2  | API                           | done        | Complete port, ABI dump in `API/General/api/api.api`; API tests are ported with the acceptance suite in stage 4 |
| 3  | Engine core                   | done        | Engine (NIO + virtual threads + coroutines, SSLEngine TLS), IO, Pages (Pebble), Conversion (Jackson 3), ErrorHandling, Redirects, Layouting |
| 4  | Testing & engine acceptance   | in progress | TestHost, acceptance infra; Engine/Api/Generators tests ported and green (111 run, 8 files pending on Inline); Practices, Security, ClientCaching, Compression, Protobuf ported; module tests being ported by agents in worktrees |
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

1. Merge the module test ports of the three worktree agents (IO/Layouting/Redirects, Conversion/Protobuf/ErrorHandling,
   Compression/Security/ClientCaching), fix production code for failing tests, commit.
2. Stage 5: Reflection, Functional, Webservices, Controllers (design in `docs/specs/reflection.md`); then remove the
   now-portable files from `pendingTests` in `Testing/Acceptance/build.gradle.kts`.
