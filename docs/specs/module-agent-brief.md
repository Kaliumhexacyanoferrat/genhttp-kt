# Brief for module porting agents

You port GenHTTP modules (C#) to Kotlin for genhttp-kt. Quality bar: idiomatic modern Kotlin, but the PUBLIC API must
mirror upstream 1:1 (every public type, function, property, parameter, default value and nullability) after applying
the translation rules.

- Repository: `C:\Work\Projects\GenHTTP\genhttp-kt` (Gradle wrapper, Kotlin 2.4.21, JDK 25, explicitApi, warnings are errors)
- Upstream: `C:\Work\Projects\GenHTTP\GenHTTP` (ignore `bin`/`obj`)

## Read first (mandatory)

1. `PORTING.md` — the porting rulebook (naming, type mapping, fluent builders, classpath, files/JvmName rule).
2. The ported API: `API/General/src/main/kotlin` (ABI summary in `API/General/api/api.api`).
3. The ported modules your module depends on (their `src/main/kotlin` and `api/*.api`), e.g. `Modules/IO`.

## Rules

- Target: `Modules/<Name>/src/main/kotlin`, package `org.genhttp.modules.<lowercasename>`; directories omit the root
  package (`Modules/Layouting/Provider/LayoutBuilder.cs` → `Modules/Layouting/src/main/kotlin/provider/LayoutBuilder.kt`).
  One Kotlin file per upstream C# file. Embedded resources go to
  `src/main/resources/org/genhttp/modules/<lowercasename>/...` and are loaded with
  `Resource.fromClasspath(SomeClassOfTheModule::class, "File.html")`.
- No `I` prefix, `Async` suffix dropped (`suspend fun`), camelCase members. Static factory classes whose name clashes with an
  API interface become companion extensions (see PORTING.md). Other factories are `object`s (`object Layout { fun create() }`).
- **Fluent builders exactly like upstream.** No DSL lambdas, no extra `block` parameters.
- Extension methods → extension functions in files annotated `@file:JvmName("<C# static class name>")`.
  Generic builder constraints `where T : IHandlerBuilder<T>` → `<T : HandlerBuilder.WithConcerns<T>>`.
- Types: `Stream` → `InputStream`/`OutputStream`, `IBufferWriter` → `WritableByteChannel`, `ReadOnlyMemory<byte>` →
  read-only `ByteBuffer` (never move someone else's buffer position), `DateTime` → `Instant`, `TimeSpan` →
  `kotlin.time.Duration`, `Type` → `KType` where nullability/generics matter else `KClass<*>`, `CultureInfo` → `Locale`,
  `Func<..., ValueTask<T>>` → `suspend (...) -> T`, `Action<IResponseBuilder>` → `ResponseBuilder.() -> Unit`,
  `IDisposable` → `AutoCloseable`, `out` parameters → nullable return values.
- Keep upstream visibility (`internal` stays `internal`, public classes stay public). KDoc on every public declaration
  (port the XML docs).
- Keep behavior identical: status codes, header names and values, error messages, edge cases, ordering.
- Do NOT modify the API module, the engine, other modules, build files (except the `build.gradle.kts` of the module you
  port, and only to add dependencies listed in `gradle/libs.versions.toml`), docs or tests. If something you need is
  missing elsewhere, stop and report it instead of working around it.
- Do not write tests (acceptance tests are ported separately).

## Workflow

1. Port all files of the module.
2. `./gradlew :modules-<name>:compileKotlin --console=plain` until clean (other agents may be building in parallel; only
   build your projects).
3. `./gradlew :modules-<name>:updateKotlinAbi --console=plain` and review `Modules/<Name>/api/*.api`: it must contain
   exactly the translated upstream public surface (make helpers `internal`/`private`).

## Report

- List of files.
- Mapping of every public upstream type/factory/extension to its Kotlin counterpart (one line each).
- Every deviation from upstream API or behavior, explicitly.
- Anything you think is missing or wrong in modules you depend on.
