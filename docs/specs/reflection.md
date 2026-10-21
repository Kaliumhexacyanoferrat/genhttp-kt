# Reflection module design

The Reflection module (`Modules/Reflection`, `org.genhttp.modules.reflection`) drives Functional (Inline),
Webservices, Controllers and parts of Authentication/DependencyInjection/OpenApi. Upstream uses runtime reflection plus
Roslyn code generation. genhttp-kt uses **kotlin-reflect only** (no code generation).

## Discovery

| Source                      | How                                                                                       |
|-----------------------------|-------------------------------------------------------------------------------------------|
| Webservice/controller class | `KClass.memberFunctions` filtered by the method annotation (`@ResourceMethod`, `@ControllerAction`, ...) |
| Inline suspend lambda       | `fn.reflect()` (kotlin.reflect.jvm, `@OptIn(ExperimentalReflectionOnLambdas::class)`) — works because suspend lambdas are compiled to classes with metadata; plain (indy) lambdas yield `null` → throw a helpful `IllegalArgumentException` ("use a suspend lambda with typed parameters or a function reference") |
| Inline function reference   | `KFunction<*>` (`::handler`, bound or unbound top-level/member references)                |

## Signature analysis (`SignatureAnalyzer`)

Per `KParameter` (skip instance/extension receivers):

1. Name matches a path argument of the route (case-insensitive) → `Path`.
2. An injector of the `InjectionRegistry` supports `type.classifier` → `Injected`.
3. Type is `java.io.InputStream` → `Streamed`.
4. Formatter can handle the type (`formatters.canHandle(classifier)`) or the type is nullable and its classifier can be
   formatted → `Body` if annotated `@FromBody`, else `Query`.
5. Otherwise → `Content` (deserialized with the serialization registry).

`OperationArgument(name: ByteString, type: KType, source, isOptional: Boolean)` — Kotlin cannot read default values,
so instead of `DefaultValue` the argument knows whether the parameter is optional. Missing optional arguments are
omitted from the call so Kotlin applies its own default (`callSuspendBy`). Missing non-optional arguments are passed
as `null` if the type is nullable, otherwise the Kotlin default of primitives (0/false) as upstream's
`Activator.CreateInstance` would.

## Result analysis

The return type (`KFunction.returnType`; suspend functions return `T` directly):

- `Unit`/`Nothing` → `None`
- `Handler`, `HandlerBuilder`, `Response`, `ResponseBuilder` (subtypes) → `Dynamic`; `Handler`/`HandlerBuilder` make
  the route a wildcard route
- `InputStream`, `ByteArray`, `ByteBuffer` → `Binary`
- `Result<T>` → analyze `T`
- formatter can handle → `Formatter`
- else → `Serializer`

## Invocation

`MethodHandler` precomputes an invoker at `prepare()`:

- Class members / function references: `KFunction.callSuspendBy(map)` (`ExecutionMode.Reflection`), or, for
  `ExecutionMode.Auto` (default), `callSuspend(*positional)` when no optional argument is omitted (falls back to
  `callSuspendBy`). `isAccessible = true` for non-public declarations of test classes.
- Suspend lambdas: invoke the lambda's synthetic `invoke(Object..., Continuation)` method via a cached
  `MethodHandle` inside `suspendCoroutineUninterceptedOrReturn`.
- `InvocationTargetException` is unwrapped (upstream rethrows the inner exception).

## Interceptors

C# generic attribute `InterceptWithAttribute<T>` becomes a meta annotation:

```kotlin
@Target(AnnotationTarget.ANNOTATION_CLASS, AnnotationTarget.FUNCTION)
annotation class InterceptWith(val interceptor: KClass<out OperationInterceptor>)

@InterceptWith(RoleInterceptor::class)
annotation class RequireRole(vararg val roles: String)
```

`InterceptorAnalyzer` collects interceptors from `@InterceptWith` placed directly on the function and from function
annotations whose annotation class carries `@InterceptWith`; it instantiates the interceptor (no-arg constructor) and
calls `configure(annotation)` with the annotation found on the function.

## Annotations

| Upstream                                  | Kotlin                                                               |
|-------------------------------------------|----------------------------------------------------------------------|
| `MethodAttribute` (base, implements `IMethodConfiguration`) | `open class MethodAttribute(vararg methods: Method) : MethodConfiguration` (programmatic use; Kotlin annotations cannot inherit) |
| `ResourceMethodAttribute(Method, string? path)` | `@ResourceMethod(val requestMethod: Method = Method.Get, val path: String = "")` converted to a `MethodConfiguration` by the framework |
| `FromBodyAttribute`                       | `@FromBody` (VALUE_PARAMETER)                                        |
| `InterceptWithAttribute<T>`               | `@InterceptWith(T::class)` meta annotation (see above)              |

## Functional (Inline)

`InlineBuilder` keeps `any/get/head/post/put/delete(path?, function)` and `on(function, methods?, path?)`:

- Members: arity-0 suspend lambdas (`get { "x" }`), `KFunction<*>` overloads, `on(function: Function<*>, ...)`.
- Extensions (generated, arities 1..16, file `InlineBuilderExtensions.kt` generated by `tools/generate_inline.py`):
  `fun <P1, R> InlineBuilder.get(fn: suspend (P1) -> R): InlineBuilder` etc. Splitting members and extensions keeps
  `{ "x" }` unambiguous (verified in STATUS.md findings).
