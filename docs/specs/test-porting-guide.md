# Porting acceptance tests

Upstream: `../GenHTTP/Testing/Acceptance` (MSTest). Target: `Testing/Acceptance/src/test/kotlin`
(JUnit 6 + kotlin.test), Gradle project `:testing-acceptance`.

## Hard rules

1. **Faithful 1:1 translation.** Same class names, same test method names with the first letter lower-cased
   (`TestBuilder` → `testBuilder`), same inputs, same expected values, same status codes, same header names and values,
   same number of assertions. Never change an expectation to make a test pass.
2. You only write test code. Never modify production code (`src/main`), build files of other projects or docs. If a
   test fails, keep it as it is and report it (with the failure message) — the lead fixes the production code.
3. No `@Disabled`, no assumptions, no empty tests, no try/catch swallowing assertion errors.
4. Tests that cannot be ported 1:1 because of a platform difference are ported as closely as possible and reported
   explicitly (test name, what differs, why).
5. Run `python tools/test_parity.py --suite acceptance --filter <Area>` for the areas you ported: 0 missing,
   0 problems.

## Layout

- `Engine/BasicTests.cs` → `src/test/kotlin/engine/BasicTests.kt`, package `org.genhttp.testing.acceptance.engine`.
- `Modules/Layouting/LayoutTests.cs` → `src/test/kotlin/modules/layouting/LayoutTests.kt`, package
  `org.genhttp.testing.acceptance.modules.layouting`.
- Nested helper types (services, models) become nested or top-level classes in the same file. Types used via
  reflection (webservices, controllers, models for serialization) must be public (Kotlin default) and non-private.
- Shared infrastructure already exists (do not duplicate): `MultiEngineTest`, `EngineProvider`, `AssertX`,
  `assertStatus`, `wrap()`, `getETag()`, `test { }`, `Constants` (package `org.genhttp.testing.acceptance`) and
  `FunctionalHandler`, `AsyncFunctionalHandler`, `WrappingHandlerBuilder` (upstream `HandlerBuilder`), `Chain`,
  `FileUtil`, `NonSeekableResource`, `NonSeekableStream`, `Security` (package `...acceptance.utilities`).
- Test resources live in `src/test/resources/Resources/...` (same files as upstream `Resources`).

## Translation table

| C# (upstream test)                                                   | Kotlin                                                                     |
|----------------------------------------------------------------------|----------------------------------------------------------------------------|
| `[TestClass] public sealed class XTests`                             | `class XTests`                                                             |
| `[TestMethod] [MultiEngineTest] public async Task TestX(TestEngine engine)` | `@MultiEngineTest fun testX(engine: ServerEngine) = test { ... }`  |
| `[TestMethod] public async Task TestX()`                             | `@Test fun testX() = test { ... }`                                         |
| `[TestMethod] public void TestX()`                                   | `@Test fun testX() { ... }`                                                |
| `[DataRow(...)]`                                                     | `@ParameterizedTest` + `@ValueSource`/`@CsvSource`/`@MethodSource`        |
| `await using var runner = await TestHost.RunAsync(h, engine: engine);` | `TestHost.run(h, engine = engine).use { runner -> ... }`                 |
| `using var response = await runner.GetResponseAsync("/x");`          | `val response = runner.getResponse("/x")`                                  |
| `await response.AssertStatusAsync(HttpStatusCode.OK);`               | `response.assertStatus(ResponseStatus.Ok)` (use the `Int` overload for codes `ResponseStatus` lacks) |
| `await response.GetContentAsync()`                                   | `response.getContent()`                                                    |
| `await response.GetContentAsync<T>()`                                | `response.getContent<T>()`                                                 |
| `response.GetHeader("X")`, `GetContentHeader("X")`                   | `response.getHeader("X")`, `response.getContentHeader("X")`               |
| `response.StatusCode`                                                | `response.statusCode()`                                                    |
| `response.Content.Headers.ContentLength`                             | `response.getContentHeader("Content-Length")?.toLong()`                    |
| `var request = runner.GetRequest("/x"); request.Method = HttpMethod.Post;` | `val request = runner.getRequest("/x", "POST")` or `.method("POST", publisher)` |
| `request.Content = new StringContent("abc")`                         | `.method("POST", BodyPublishers.ofString("abc")).header("Content-Type", "text/plain; charset=utf-8")` (.NET adds this content type automatically — add it explicitly) |
| `new StringContent(s, Encoding.UTF8, "application/json")`            | `BodyPublishers.ofString(s)` + `header("Content-Type", "application/json; charset=utf-8")` |
| `new ByteArrayContent(bytes)` / `StreamContent(stream)`              | `BodyPublishers.ofByteArray(bytes)` / `BodyPublishers.ofInputStream { stream }` (unknown length → chunked) |
| `request.Headers.Add("X", "Y")`                                      | `.header("X", "Y")` (restricted headers such as Host are allowed in the test JVM) |
| `TestHost.GetClient(...)`                                            | `TestHost.getClient(...)` (`creds` takes a `java.net.PasswordAuthentication`, `cookies` a `java.net.CookieManager`) |
| `Assert.AreEqual(a, b)` / `IsTrue` / `IsNull` / `ThrowsExactly<T>`   | `assertEquals(a, b)` / `assertTrue` / `assertNull` / `assertFailsWith<T>`  |
| `AssertX.Contains(...)`                                              | `AssertX.contains(...)`                                                    |
| `Resource.FromAssembly("File.txt")`                                  | `Resource.fromClasspath("Resources/File.txt")`                             |
| `ResourceTree.FromAssembly("Resources")`                             | `ResourceTree.fromClasspath("Resources")`                                  |
| `Path.GetTempFileName()` / temp dirs                                 | `Files.createTempFile(...)` / `Files.createTempDirectory(...)`             |
| `await Task.Delay(n)`                                                | `delay(n.toLong())`                                                        |
| `TcpClient` / raw sockets                                            | `java.net.Socket` (blocking, inside `test { }` is fine)                    |
| `HttpClient` HTTP/1.0 requests                                       | raw socket (the JDK client only supports HTTP/1.1 and 2)                   |
| C# lambdas `r => ...` as handlers                                    | Kotlin lambdas; `Handler.from { r -> ... }`                                |
| `Inline.Create().Get((int a) => ...)`                                | `Inline.create().get { a: Int -> ... }` (suspend lambdas)                  |
| `ServerEngine.Kestrel` / `Ioxide` specific branches                  | drop (only the internal engine exists); keep the generic part              |

API naming follows docs/PORTING.md (no `I` prefix, camelCase, `Async` dropped, companion factories such as
`Resource.fromString`, `Handler.from`, `Content.from`, fluent builders). Read the ported production code of the module
under test (`src/main/kotlin` and `api/*.api`) to find the exact Kotlin names.

## Working efficiently

Your context is resent on every step, so everything you read stays expensive until you finish.

- Never read full Gradle output. Filter it:
  `./gradlew <task> --console=plain -q 2>&1 | grep -E "^e:|^w:|FAILED|went wrong" -A3 | head -40`.
  For failing tests, read only the `<failure message=...>` of the failing classes in
  `Testing/Acceptance/build/test-results/test/*.xml` (e.g. with a short `python`/`grep` extraction).
- Search before you read: `grep -n` for the names you need in `api/*.api` dumps and sources, then read only the
  relevant line ranges. Do not read whole ABI dumps, unrelated modules or `docs/DEVIATIONS.md` as a whole.
- Read each file once; do not re-read files you have already seen or written.
- Run only your own tests and builds, never the whole build.
- Keep the final report compact: facts, names, failures and deviations, no narration.
