# MCP (Model Context Protocol) module

Upstream `Modules/Mcp` (GenHTTP 11.2, commit d73caebc) provides tools to AI agents via the Model Context Protocol
(Streamable HTTP transport, stateless revision 2026-07-28 and the revisions 2025-03-26 to 2025-11-25). genhttp-kt
ports it as `Modules/Mcp` (`:modules-mcp`, artifact `genhttp-modules-mcp`, package `org.genhttp.modules.mcp`). The
protocol handling (JSON-RPC messages, versions, origin policy, caching headers, error codes, log lines) is ported 1:1;
only the platform types change.

## Dependencies

- `api(project(":modules-reflection"))` (Reflection brings Conversion, IO and Jackson 3).
- `api(libs.jakarta.validation)` — `jakarta.validation:jakarta.validation-api` (annotations only, already used
  transitively by swagger-core), see [Validation](#validation).
- Registered in `settings.gradle.kts`, `Packages/Full`, `Packages/FullNetty` and `Testing/Acceptance` like every module.
- `Modules/DependencyInjection` gains `DependentTools` (upstream `DependentTools.cs`) and the `InstanceProvider`
  addition of the same commit; it depends on `:modules-mcp`.

## Public surface

| Upstream                                                     | Kotlin                                                                                                     |
|--------------------------------------------------------------|------------------------------------------------------------------------------------------------------------|
| `Tools.Create()`                                             | `object Tools { fun create(): ToolsHandlerBuilder }`                                                       |
| `[Tool(name)] { Title, ReadOnly, Destructive, Idempotent, OpenWorld }` | `@Tool(name = "", title = "", readOnly = [], destructive = [], idempotent = [], openWorld = [])` — the hints are tri-state upstream (`bool?` behind the property: unset hints are not sent), so they are `BooleanArray`s with at most one entry (`@Tool(readOnly = [true])`), like `ApiResponseHeader.status` |
| `ToolsHandlerBuilder : IHandlerBuilder<ToolsHandlerBuilder>` | `class ToolsHandlerBuilder : HandlerBuilder.WithConcerns<ToolsHandlerBuilder>` (package `org.genhttp.modules.mcp.provider`) |
| `.Add<T>()`                                                  | `inline fun <reified T : Any> add()` (delegating to a `KClass` overload)                                   |
| `.Add(object instance)`                                      | `fun add(instance: Any)`                                                                                   |
| `.Add(string name, string description, Delegate function)`   | `fun add(name: String, description: String, function: KFunction<*>)` for function references plus suspend lambda overloads of arity 0..N (arity 0 as member, the others as extension functions in the module package), exactly like `InlineBuilder` of the Functional module (`Modules/Functional/src/main/kotlin/InlineBuilderExtensions.kt`) |
| `.Add(IConcernBuilder)`                                      | `fun add(concern: ConcernBuilder)`                                                                         |
| `.InstanceProvider(Func<Type, IRequest, ValueTask<object>>)` | `fun instanceProvider(provider: suspend (KClass<*>, Request) -> Any)`                                      |
| `.Injectors(IBuilder<InjectionRegistry>)`                    | `fun injectors(registry: Builder<InjectionRegistry>)`                                                      |
| `.Server(name, version, title = null)`                       | `fun server(name: String, version: String, title: String? = null)`                                         |
| `.Instructions(string)`, `.AllowOrigin(string)`              | `instructions(...)`, `allowOrigin(...)`                                                                    |
| `.CacheDuration(TimeSpan)` (`ArgumentOutOfRangeException`)   | `cacheDuration(duration: kotlin.time.Duration)` (`IllegalArgumentException` if negative)                    |
| `.Serialization(JsonSerializerOptions)`                      | `serialization(mapper: JsonMapper)` (Jackson 3)                                                            |
| `.Build()` (`InvalidOperationException`)                     | `build(): Handler` (`IllegalStateException`)                                                               |
| `ToolResult` (+ `Text`, `From`, `Json`, `Error`)             | `class ToolResult(content: List<ToolContent>?, structured: Any? = null, isError: Boolean = false)` with companion `text`, `from(vararg)`, `json`, `error` |
| `abstract record ToolContent`, `TextContent`, `ImageContent(ReadOnlyMemory<byte>, string)`, `AudioContent`, `ResourceLinkContent(Uri, Name) { Title, Description, MimeType }` | `sealed class ToolContent` with data classes `TextContent(text)`, `ImageContent(data: ByteBuffer, mimeType)`, `AudioContent(data: ByteBuffer, mimeType)`, `ResourceLinkContent(uri, name, title = null, description = null, mimeType = null)`; writing them stays `internal` |
| `DependentTools.Create()` (DependencyInjection)              | `object DependentTools { fun create(): ToolsHandlerBuilder }`                                              |

Everything else is `internal`, as upstream. Follow `docs/PORTING.md` for naming, files (one Kotlin file per C# file,
directories `provider/`, `protocol/`, `schema/`) and KDoc.

## Platform mappings

- **JSON**: System.Text.Json → Jackson 3 (`tools.jackson.*`): `JsonNode`/`JsonObject`/`JsonArray` → `JsonNode`/
  `ObjectNode`/`ArrayNode`, `Utf8JsonWriter` → `JsonGenerator`, `JsonSerializerOptions` → `JsonMapper`.
  `JsonBuffer` (reading request bodies) maps to reading the body via `RequestBody.asMemory()`/`asStream()`.
- **Default serialization** mirrors `McpSerialization.Default` (web defaults): camel case properties, case-insensitive
  reading, `null` values omitted, enums as strings, no HTML escaping, numbers accepted as strings when reading,
  Kotlin module. It is a mapper of its own (the Conversion module writes enums as numbers); start from
  `JsonFormat.getDefaultOptions()` where it helps. Nullable members are optional when reading (`OptionalNullables`).
- **Nullability**: `RespectNullableAnnotations`/`TreatNullObliviousAsNonNullable` → Kotlin nullability (`KType`,
  `KProperty.returnType`). Non-nullable parameters/properties without a default value are required.
- **Schemas**: `JsonSchemaExporter` → a generator of the module based on the Kotlin types (generics, nullability,
  enums, collections, maps, primitives, `java.time` types) and on the properties written by the mapper (introspect
  with `mapper._serializationContext().introspectBeanDescription(...)`, as `Modules/OpenApi/.../SerializedNames.kt`
  does). The produced keywords must equal upstream's (`JsonSchemaExporter` with the transformations of
  `SchemaGenerator.cs`) for every case the tests check: types, `required` and its order, nullability, `enum`, `items`,
  recursion, descriptions.
- **Default values**: kotlin-reflect cannot read default values (see the Reflection deviation), so no `default`
  keyword is emitted. Optional parameters are omitted from the call, like the Reflection module does.
- **Descriptions**: `[Summary]`/`[Remarks]` of the API as upstream; `System.ComponentModel.DescriptionAttribute` →
  Jackson's `@JsonPropertyDescription` (the JVM annotation describing JSON members).
- **Results**: `Task<T>`/`ValueTask<T>` → suspend functions; `void`/`Unit` results as upstream treats `void`.
- **Instances**: `Activator.CreateInstance(type)` → the parameterless constructor made accessible (see
  `ServiceResourceBuilder.newInstance`), same error message if there is none.
- **Server identity**: the entry assembly → the `Implementation-Title`/`Implementation-Version` of the package of the
  main class if available, `"GenHTTP"`/`"1.0.0"` otherwise.
- **Logging**: `ILogger` + `LoggerMessage` → SLF4J from `server.logging`, same messages and levels (elapsed times
  formatted like `0.##`).
- **Interceptors**: `IDocumentedInterceptor` → `DocumentedInterceptor`, methods found via `TypeAnalyzer.findMethods`.

## Validation

`System.ComponentModel.DataAnnotations` is a .NET platform API. Its JVM counterpart is Jakarta Bean Validation; the
module reads the constraint annotations of `jakarta.validation.constraints`, advertises them in the schema and
validates the arguments itself (like upstream calls `ValidationAttribute.IsValid`), no validation provider needed:

| Upstream                                   | Kotlin                                                    | Schema                                     |
|--------------------------------------------|-----------------------------------------------------------|--------------------------------------------|
| `[Range(int, int)]`                        | `@Min` + `@Max`                                           | `minimum` / `maximum`                      |
| `[Range(double, double)]` (+ `MinimumIsExclusive`, `MaximumIsExclusive`) | `@DecimalMin` / `@DecimalMax` (`inclusive = false`) | `minimum`/`maximum` or `exclusiveMinimum`/`exclusiveMaximum` |
| `[Length]`, `[StringLength]`, `[MinLength]`, `[MaxLength]` | `@Size(min, max)`                         | `minLength`/`maxLength` (strings), `minItems`/`maxItems` (collections, arrays) |
| `[RegularExpression]`                      | `@Pattern` (the whole value must match)                   | `pattern`                                  |
| `[EmailAddress]`                           | `@Email` (validated like .NET: one `@`, not at the start or end) | `format: email`                     |
| `[Required]`                               | `@NotNull` (`@NotBlank` additionally rejects empty strings) | —                                        |
| `[Url]`                                    | no counterpart: arguments of type `java.net.URI`          | `format: uri`                              |
| `[AllowedValues]`                          | no counterpart: enum types                                | `enum`                                     |
| `[Range(typeof(DateTime), ...)]`           | no counterpart: `@Past`/`@PastOrPresent`/`@Future`/`@FutureOrPresent` | —                              |

Annotations on constructor parameters of models count for their properties (`[property: Range]` upstream). Violation
messages use the default messages of Bean Validation (e.g. `must be less than or equal to 5`, `size must be between
1 and 3`) in upstream's format (`The arguments are invalid:\n- skills[0].level: <message>`); values that cannot be
deserialized are reported for their argument in the same format.

## Tests

Upstream `Testing/Acceptance/Modules/Mcp/*` → `Testing/Acceptance/src/test/kotlin/modules/mcp/` (package
`org.genhttp.testing.acceptance.modules.mcp`), `McpClient` and `TestTools` as helpers in the same package;
`Modules/DependencyInjection/ToolTests.cs` → `modules/dependencyinjection/ToolTests.kt`. Follow
`docs/specs/test-porting-guide.md`; models use the Kotlin types named above (`URI`, enums, Bean Validation
annotations). Differences caused by the platform (no `default` keyword, Bean Validation messages) are listed in the
deviation log.
