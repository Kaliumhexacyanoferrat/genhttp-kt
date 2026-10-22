# Dependency injection (Microsoft.Extensions.DependencyInjection → Koin)

Upstream `Modules/DependencyInjection` integrates `IServiceProvider` / `IServiceScope`. genhttp-kt uses Koin
(`io.insert-koin:koin-core`, catalog alias `libs.koin.core`). The public surface mirrors upstream 1:1; only the
container types change.

## Type mapping

| Upstream                                   | Kotlin                                                                 |
|--------------------------------------------|------------------------------------------------------------------------|
| `IServiceProvider`                         | `org.koin.core.Koin`                                                   |
| `IServiceScope`                            | `org.koin.core.scope.Scope`                                            |
| `services.AddScoped<T>()`                  | `scope(RequestScope) { scoped { T(get()) } }`                          |
| `services.AddSingleton` / `AddTransient`   | `single { }` / `factory { }`                                           |
| `ActivatorUtilities.CreateInstance<T>(sp)` | internal `Activator.create(KClass, Scope)` (see below)                 |
| `IServiceProviderIsService.IsService(t)`   | definition lookup (see `DependencyInjector`)                           |

`RequestScope` is a public `Qualifier` (`named("genhttp.request")`), declared in `Integration.kt`. Koin needs a scope
qualifier for scoped definitions; this is the documented equivalent of `AddScoped`.

## Behavior

- `ServerHost.addDependencyInjection(koin: Koin)` adds the `InjectionConcernBuilder`.
- `InjectionConcern.prepare(server)` stores the `Koin` in `server.properties` (same key as upstream), then prepares the
  content.
- `InjectionConcern.handle(request)`: `koin.createScope(<unique id>, RequestScope)`, store it in
  `request.properties`, `try { content.handle(request) } finally { scope.close() }`. Like upstream, the scope is closed
  when `handle` returns (before the response body is written).
- `getServiceProvider()` / `getServiceScope()` throw `IllegalStateException` with the upstream messages.
- `DependencyInjector.supports(server, type)`: true if Koin has an unqualified definition whose primary or secondary
  type is `type`, in the root scope or in `RequestScope`. Must not instantiate anything. Use the definition registries
  (`@OptIn(KoinInternalApi::class)` is acceptable; verify the API against the Koin 4.2.2 jar).
- `DependencyInjector.getValue(...)`: `request.getServiceScope().getOrNull<Any>(type)`. Request scopes link to the
  root scope, so singletons and factories resolve as well.
- `InstanceProvider.provide(request, type)` and `HandlerResolver.obtain`: `scope.getOrNull(type) ?: Activator.create(type, scope)`.
- `Activator.create` (replaces `ActivatorUtilities`): pick the primary constructor (else the public constructor with the
  most parameters), resolve every parameter by its classifier from the scope. If a parameter cannot be resolved:
  optional → omitted (`callBy`), nullable → `null`, otherwise `IllegalStateException("Unable to resolve service for
  type '<param type>' while attempting to activate '<type>'.")`. Uses kotlin-reflect.

## Public surface

- `object Dependent { inline fun <reified T : DependentConcern> concern(): ConcernBuilder; inline fun <reified T : DependentHandler> handler(): HandlerBuilder }`
  (non-inline `KClass` overloads are fine as the implementation; reified versions are the user-facing API).
- `interface DependentHandler { suspend fun handle(request: Request): Response? }`
- `interface DependentConcern { suspend fun handle(content: Handler, request: Request): Response? }`
- `object DependentInline { fun create(injectors: InjectionRegistryBuilder? = null, serializers: Builder<SerializationRegistry>? = null, formatters: Builder<FormatterRegistry>? = null): InlineBuilder }`
- `inline fun <reified T : Any> LayoutBuilder.addDependentService(path: String, injectors = null, serializers = null, formatters = null): LayoutBuilder`
- `inline fun <reified T : Any> LayoutBuilder.addDependentController(...)` (same parameters)
- `inline fun <reified T : ImperativeHandler> ImperativeWebsocketBuilder.dependentHandler()` and the reactive variant
- `Integration`: `addDependencyInjection`, `Server.getServiceProvider()`, `Request.getServiceProvider()`, `Request.getServiceScope()`
- `infrastructure/`: `DependencyInjector`, `InstanceProvider` public as upstream; the rest internal as upstream.

## Usage (for the README)

```kotlin
val koin = koinApplication {
    modules(module {
        single { AwesomeService() }
        scope(RequestScope) { scoped { RequestTracker() } }
    })
}.koin

val app = Layout.create()
    .addDependentService<MyService>("service")

Host.create()
    .handler(app)
    .addDependencyInjection(koin)
    .run()
```
