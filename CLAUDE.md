# CLAUDE.md

Guidance for agents working on genhttp-kt, the Kotlin port of [GenHTTP](https://github.com/Kaliumhexacyanoferrat/GenHTTP).

**Read first:** [STATUS.md](STATUS.md) (where the work stands), [PORTING.md](PORTING.md) (how C# maps to Kotlin —
mandatory), [SYNC.md](SYNC.md) (how upstream changes are ported). The initial port follows [PLAN.md](PLAN.md).

## Why this project exists

genhttp-kt is an automated port of GenHTTP to Kotlin, created by Claude as a proof of concept. It answers the question
whether a framework can be kept in sync across ecosystems by agents: GenHTTP stays the source of truth, genhttp-kt
follows it commit by commit. The port keeps GenHTTP's public API and behavior 1:1 while being idiomatic, modern Kotlin
(coroutines, NIO, virtual threads). See the philosophy section of PORTING.md.

## Commands

```bash
./gradlew build                          # compile, test, ABI check, coverage verification
./gradlew test                           # all tests
./gradlew :testing-acceptance:test --tests "org.genhttp.testing.acceptance.engine.BasicTests"
./gradlew updateKotlinAbi                # refresh api/*.api dumps after intended public API changes
./gradlew koverHtmlReport                # coverage report (build/reports/kover)
./gradlew :playground:run                # demo server on port 8080
python tools/test_parity.py              # compare ported tests with upstream (needs ../GenHTTP, ../Glyph11)
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

## Rules

- **Follow PORTING.md.** Naming (no `I` prefix, no `Async` suffix), type mapping, fluent builders, reflection approach.
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
- Commits reference upstream commits with `Upstream-Commit:` trailers when porting upstream changes (see SYNC.md).

## Delegating to sub-agents

When the lead session delegates module work:

1. One agent ports the upstream tests only; another implements the module and must not modify test files.
2. The lead verifies `git diff --stat` (no test changes in implementation work), runs `python tools/test_parity.py`,
   the full build, and reviews production diffs.
3. Agents get the upstream file list, the relevant PORTING.md rules and the exact target paths/packages.
