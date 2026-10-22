# genhttp-kt

genhttp-kt is a lightweight, modular web server for Kotlin with a strong focus on developer experience. It allows
developers to quickly create web services on the JVM and concentrate on the functionality rather than on configuration
files or complex concepts. genhttp-kt is the Kotlin port of [GenHTTP](https://github.com/Kaliumhexacyanoferrat/GenHTTP)
(see [About the Port](#about-the-port)).

[![Build](https://github.com/Kaliumhexacyanoferrat/genhttp-kt/actions/workflows/build.yml/badge.svg)](https://github.com/Kaliumhexacyanoferrat/genhttp-kt/actions/workflows/build.yml) [![View - Documentation](https://img.shields.io/badge/view-Documentation-AB54FF)](https://genhttp.org/documentation/) [![Discord](https://discordapp.com/api/guilds/1177529388229734410/widget.png?style=shield)](https://discord.gg/PRkwKrnrB4)

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

## Samples

The [playground](./Playground/) project provides a quick starting point to view sample code. See
[the GenHTTP documentation](https://genhttp.org/documentation/content/) for all available capabilities. The
documentation uses C#, see [Coming from GenHTTP](docs/PORT.md#coming-from-genhttp) for how its samples translate to
Kotlin.

## Support

If you encounter issues implementing your application, feel free to
[join the GenHTTP Discord community](https://discord.gg/PRkwKrnrB4) to get help. Issues specific to genhttp-kt can be
reported in [this repository](https://github.com/Kaliumhexacyanoferrat/genhttp-kt/issues).

## Building the Server

To build the server from source, clone this repository and run the playground project:

```sh
git clone https://github.com/Kaliumhexacyanoferrat/genhttp-kt.git
cd genhttp-kt
./gradlew :playground:run
```

This will build the playground project with all the server dependencies and launch the server process on port 8080.

## About the Port

genhttp-kt is an automated port of GenHTTP, generated and maintained by [Claude](https://claude.ai) as a proof of
concept: can a framework be kept in sync across ecosystems by agents? GenHTTP stays the source of truth and genhttp-kt
follows it commit by commit. The public API and behavior mirror GenHTTP, expressed in idiomatic Kotlin (coroutines,
NIO and virtual threads), so the [GenHTTP documentation](https://genhttp.org/documentation/) applies to this project
as well.

Versions follow GenHTTP: `11.1.0.0` is based on GenHTTP `11.1.0`, the last component counts port-only releases. Features
and behavioral changes are contributed to GenHTTP first and reach genhttp-kt with the next sync (see
[CONTRIBUTING.md](CONTRIBUTING.md)).

[docs/PORT.md](docs/PORT.md) describes how C# translates to Kotlin, how the engine works, how the port is kept in sync
and verified, and where it intentionally differs from GenHTTP.

## Thanks

- Ported from [GenHTTP](https://github.com/Kaliumhexacyanoferrat/GenHTTP) and [Glyph11](https://github.com/dotnet-web-stack/Glyph11)
- Powered by [Kotlin](https://kotlinlang.org/), [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) and the [JDK](https://openjdk.org/)
- Modules implemented with [Jackson](https://github.com/FasterXML/jackson) | [swagger-core](https://github.com/swagger-api/swagger-core) | [Pebble](https://pebbletemplates.io/) | [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/) | [Nimbus JOSE + JWT](https://connect2id.com/products/nimbus-jose-jwt) | [Koin](https://insert-koin.io/) | [Brotli4j](https://github.com/hyperxpro/Brotli4j) | [zstd-jni](https://github.com/luben/zstd-jni)
