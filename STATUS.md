# Status

Updated at the end of every stage (see [PLAN.md](PLAN.md)). A fresh session resumes from the first stage that is not
done.

| #  | Stage                         | State       | Notes |
|----|-------------------------------|-------------|-------|
| 0  | Scaffolding                   | done        | Gradle 9.8.2, Kotlin 2.4.21, JDK 25, conventions in `build-logic`, test parity tool, CI skeleton |
| 1  | Glyph11                       | open        | |
| 2  | API                           | open        | |
| 3  | Engine core                   | open        | |
| 4  | Testing & engine acceptance   | open        | |
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

Stage 1: port Glyph11 (`../Glyph11/src/Glyph11`) and its tests (`../Glyph11/tests/Tests`).
