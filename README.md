# genhttp-kt

genhttp-kt is a lightweight, modular web server for Kotlin with a strong focus on developer experience. It is a port of
the [GenHTTP](https://github.com/Kaliumhexacyanoferrat/GenHTTP) web server to modern Kotlin and the JVM, allowing
developers to quickly create web services and concentrate on the functionality rather than on configuration files or
complex concepts.

> [!NOTE]
> genhttp-kt is an automated port of GenHTTP, generated and maintained by [Claude](https://claude.ai) as a proof of
> concept. GenHTTP is the source of truth: changes are ported from upstream by an agent following the rules in
> [SYNC.md](SYNC.md). The public API mirrors GenHTTP, so the [GenHTTP documentation](https://genhttp.org/documentation/)
> applies to this project as well.

[![Build](https://github.com/Kaliumhexacyanoferrat/genhttp-kt/actions/workflows/build.yml/badge.svg)](https://github.com/Kaliumhexacyanoferrat/genhttp-kt/actions/workflows/build.yml) [![Upstream](https://img.shields.io/badge/upstream-GenHTTP-AB54FF)](https://github.com/Kaliumhexacyanoferrat/GenHTTP) [![View - Documentation](https://img.shields.io/badge/view-Documentation-AB54FF)](https://genhttp.org/documentation/) [![Discord](https://discordapp.com/api/guilds/1177529388229734410/widget.png?style=shield)](https://discord.gg/PRkwKrnrB4)

## Getting Started

genhttp-kt requires JDK 25 or newer. Add a dependency on `genhttp-full` to your project and spin off a new host:

```kotlin
// build.gradle.kts
dependencies {
    implementation("org.genhttp:genhttp-full:11.1.0.0")
}
```

```kotlin
import org.genhttp.engine.internal.Host

import org.genhttp.modules.apibrowsing.*
import org.genhttp.modules.functional.*
import org.genhttp.modules.layouting.*
import org.genhttp.modules.openapi.*
import org.genhttp.modules.practices.*

suspend fun main() {
    // use a handler of your choice (see the samples below)
    val api = Inline.create()
        .get { a: Int, b: Int -> a + b }

    val app = Layout.create()
        .add(api)
        .addOpenApi()
        .addScalar()

    Host.create()
        .handler(app)
        .defaults()
        .run() // or .start() to return immediately
}
```

Running this snippet will provide the following endpoints:

| Endpoint                           | Description                                                                       |
|------------------------------------|-----------------------------------------------------------------------------------|
| http://localhost:8080/?a=1&b=2     | Serves the API, answering requests by calculating the sum of two query arguments. |
| http://localhost:8080/openapi.json | Serves the automatically generated Open API specification of the API.             |
| http://localhost:8080/scalar/      | Serves a graphical viewer of the API, using Scalar.                               |

If you only need the engine and the basic modules (IO, layouting and best practices), depend on `genhttp-core` instead
and add the modules you need individually (`genhttp-modules-<name>`).

## Coming from GenHTTP

Code written for GenHTTP translates mechanically:

| GenHTTP (C#)                                   | genhttp-kt (Kotlin)                                 |
|------------------------------------------------|-----------------------------------------------------|
| `IHandler`, `IRequest`, `IResponseBuilder`     | `Handler`, `Request`, `ResponseBuilder`             |
| `ValueTask<IResponse?> HandleAsync(...)`       | `suspend fun handle(...): Response?`                |
| `Layout.Create().Add("api", api)`              | `Layout.create().add("api", api)`                   |
| `.Get((int a, int b) => a + b)`                | `get { a: Int, b: Int -> a + b }`                   |
| `[ResourceMethod(RequestMethod.Post)]`         | `@ResourceMethod(Method.Post)`                      |
| `Resource.FromAssembly("index.html")`          | `Resource.fromClasspath("index.html")`              |
| `Stream`                                       | `InputStream` / `OutputStream`                      |

[PORTING.md](PORTING.md) contains the complete mapping, [docs/DEVIATIONS.md](docs/DEVIATIONS.md) lists every place
where the port intentionally differs.

## Samples

The [playground](./Playground/) project provides a quick starting point to view sample code. See
[the GenHTTP documentation](https://genhttp.org/documentation/content/) for all available capabilities.

## Engine

genhttp-kt ships with its own HTTP/1.1 engine, a port of GenHTTP's internal engine. It runs every connection as a
coroutine on virtual threads, uses NIO channels for I/O and parses requests with a hardened parser ported from
[Glyph11](https://github.com/dotnet-web-stack/Glyph11). Modules are engine-independent, exactly like in GenHTTP.

## Support

If you encounter issues implementing your application, feel free to
[join the GenHTTP Discord community](https://discord.gg/PRkwKrnrB4) to get help. Issues specific to the Kotlin port can
be reported in [this repository](https://github.com/Kaliumhexacyanoferrat/genhttp-kt/issues).

## Building the Server

To build the server from source, clone this repository and run the playground project:

```sh
git clone https://github.com/Kaliumhexacyanoferrat/genhttp-kt.git
cd genhttp-kt
./gradlew :playground:run
```

This will build the playground project with all the server dependencies and launch the server process on port 8080.

## Versioning

Versions follow GenHTTP: `11.1.0.0` is the first release of genhttp-kt based on GenHTTP `11.1.0`, `11.1.0.1` would be
a port-only release on the same upstream version. See [SYNC.md](SYNC.md) for details.

## Thanks

- Ported from [GenHTTP](https://github.com/Kaliumhexacyanoferrat/GenHTTP) and [Glyph11](https://github.com/dotnet-web-stack/Glyph11)
- Powered by [Kotlin](https://kotlinlang.org/), [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) and the [JDK](https://openjdk.org/)
- Modules implemented with [Jackson](https://github.com/FasterXML/jackson) | [swagger-core](https://github.com/swagger-api/swagger-core) | [Pebble](https://pebbletemplates.io/) | [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/) | [Nimbus JOSE + JWT](https://connect2id.com/products/nimbus-jose-jwt) | [Koin](https://insert-koin.io/) | [Brotli4j](https://github.com/hyperxpro/Brotli4j) | [zstd-jni](https://github.com/luben/zstd-jni)
