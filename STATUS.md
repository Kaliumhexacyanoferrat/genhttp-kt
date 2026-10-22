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
| 6  | API documentation             | in progress | OpenApi ported; ApiBrowsing and the OpenApi tests are being ported |
| 7  | Cross-cutting modules         | in progress | Authentication, I18n, Inspection ported; DependencyInjection (Koin, see `docs/specs/dependency-injection.md`) and the tests are being ported |
| 8  | Content modules               | in progress | Files, Archives, DirectoryBrowsing, VirtualHosting ported; StaticWebsites, SinglePageApplications, LoadBalancing and the tests are being ported |
| 9  | Connection modules            | in progress | Websockets, ServerSentEvents ported; ReverseProxy and the tests are being ported |
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

Agents work in git worktrees (`.claude/worktrees/*`, branches `worktree-agent-*`); merge their branches when done.
Running when the session ended (check `git branch` for unmerged `worktree-agent-*` branches):

- Modules: ApiBrowsing + SinglePageApplications + StaticWebsites + LoadBalancing (without `proxy(...)`, add it after
  ReverseProxy is merged); ReverseProxy; DependencyInjection.
- Tests: Files + Archives + DirectoryBrowsing + VirtualHosting + Inspection + I18n; OpenApi; Websockets;
  Authentication + ServerSentEvents.

Then: tests of the last modules (ApiBrowsing, SPA, StaticWebsites, ReverseProxy, DependencyInjection, LoadBalancing),
enable `pendingTests`, stage 10 (packages, playground, benchmarks, release), coverage, API parity pass.
