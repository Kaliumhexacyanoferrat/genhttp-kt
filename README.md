# genhttp-kt

genhttp-kt is a lightweight, modular web server for Kotlin with a strong focus on developer experience. It allows
developers to quickly create web services on the JVM and concentrate on the functionality rather than on configuration
files or complex concepts. genhttp-kt is the Kotlin port of [GenHTTP](https://github.com/Kaliumhexacyanoferrat/GenHTTP)
(see [About the Port](#about-the-port)).

Website and documentation: **[genhttp.org/kotlin](https://genhttp.org/kotlin)**

[![Build](https://github.com/Kaliumhexacyanoferrat/genhttp-kt/actions/workflows/build.yml/badge.svg)](https://github.com/Kaliumhexacyanoferrat/genhttp-kt/actions/workflows/build.yml) [![View - Documentation](https://img.shields.io/badge/view-Documentation-7F52FF)](https://genhttp-kt.genhttp.run/documentation/) [![Discord](https://discordapp.com/api/guilds/1177529388229734410/widget.png?style=shield)](https://discord.gg/PRkwKrnrB4)

## Getting Started

genhttp-kt requires JDK 25 or newer. Add a dependency on `genhttp-full` to your project and spin off a new host:

```kotlin
// build.gradle.kts
dependencies {
    implementation("org.genhttp:genhttp-full:11.1.0.3")
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
and add the modules you need individually (`genhttp-modules-<name>`, see the
[list of modules](https://genhttp-kt.genhttp.run/documentation/content/#modules)). To run on the Netty engine, depend on
`genhttp-full-netty` or `genhttp-core-netty` and import `org.genhttp.engine.netty.Host` (see [Engines](#engines)).

## Documentation

The [documentation](https://genhttp-kt.genhttp.run/documentation/) describes every framework, handler, concern and
concept of genhttp-kt, with Kotlin samples that are compiled against the release they describe:

- [Getting started](https://genhttp-kt.genhttp.run/documentation/) with Gradle or Maven
- [Content](https://genhttp-kt.genhttp.run/documentation/content/) - webservices, functional handlers, controllers,
  websockets, static websites, SPAs, Open API, authentication and all other modules
- [Testing](https://genhttp-kt.genhttp.run/documentation/testing/), [running](https://genhttp-kt.genhttp.run/documentation/server/)
  and [hosting](https://genhttp-kt.genhttp.run/documentation/hosting/) an application
- [Coming from GenHTTP](https://genhttp-kt.genhttp.run/documentation/port/coming-from-genhttp/) - how C# code
  translates to Kotlin, and the [deviations](https://genhttp-kt.genhttp.run/documentation/port/deviations/) from GenHTTP

It is the documentation of GenHTTP, ported page by page, and kept in sync with GenHTTP's documentation and with
genhttp-kt the same way the port is (see [SYNC.md](docs/SYNC.md#the-website)).

## Samples

The [playground](./Playground/) project provides a quick starting point to view sample code. See
[the documentation](https://genhttp-kt.genhttp.run/documentation/content/) for all available capabilities.

## Engines

genhttp-kt runs on different engines that can simply be switched by changing a single dependency and the import of the
`Host`. Engines are tested to be compatible with all modules, so you can switch the engine without any further
adjustments.

| Engine                                                                            | Protocols | Performance                                                                                                                                 | Use Cases                                                              |
|-----------------------------------------------------------------------------------|-----------|---------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------|
| [Internal](https://genhttp-kt.genhttp.run/documentation/server/engines/internal/) | `1.1`     | [![HTTP Arena](https://img.shields.io/endpoint?url=https://www.http-arena.com/badge/genhttp-kt/h1.json)](https://www.http-arena.com/)       | Embedding; web applications behind a reverse proxy; blocking handlers  |
| [Netty](https://genhttp-kt.genhttp.run/documentation/server/engines/netty/)       | `1.1`     | [![HTTP Arena](https://img.shields.io/endpoint?url=https://www.http-arena.com/badge/genhttp-kt-netty/h1.json)](https://www.http-arena.com/) | High performance web applications on Linux via epoll or `io_uring`     |

The internal engine (`genhttp-full`, `genhttp-core`, `org.genhttp.engine.internal.Host`) serves every connection on
virtual threads, so handlers may block. The Netty engine (`genhttp-full-netty`, `genhttp-core-netty`,
`org.genhttp.engine.netty.Host`) is the counterpart of GenHTTP's ioxide engine: one event loop per CPU core serves its
connections from start to finish, including websockets and TLS via OpenSSL. Handlers run on the loop and must suspend
rather than block - move blocking calls such as JDBC to `withContext(Dispatchers.IO)`.

According to [HTTP Arena](https://http-arena.com/#sort=rps:-1&type=emerging,experimental,flagship&tuned=0&q=java%2Ckotlin),
genhttp-kt is among the fastest Kotlin web servers for HTTP/1.1: with the Netty engine it ranks second of all the Java
and Kotlin frameworks without tuning, and the internal engine is 1.4 times faster than Ktor and 2.6 times faster than
Spring Boot (results of October 24, 2026). The
[features page](https://genhttp-kt.genhttp.run/features/#server-performance) shows the full comparison.

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
NIO and virtual threads). The [documentation of genhttp-kt](https://genhttp-kt.genhttp.run/documentation/) is the
documentation of GenHTTP ported to Kotlin, so samples written for GenHTTP translate mechanically
([Coming from GenHTTP](https://genhttp-kt.genhttp.run/documentation/port/coming-from-genhttp/)).

Versions follow GenHTTP: `11.1.0.0` is based on GenHTTP `11.1.0`, the last component counts port-only releases. Features
and behavioral changes are contributed to GenHTTP first and reach genhttp-kt with the next sync (see
[CONTRIBUTING.md](CONTRIBUTING.md)).

[docs/PORT.md](docs/PORT.md) describes how C# translates to Kotlin, how the engine works, how the port is kept in sync
and verified, and where it intentionally differs from GenHTTP.

## Thanks

- Ported from [GenHTTP](https://github.com/Kaliumhexacyanoferrat/GenHTTP) and [Glyph11](https://github.com/dotnet-web-stack/Glyph11)
- Powered by [Kotlin](https://kotlinlang.org/), [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) and the [JDK](https://openjdk.org/)
- Netty engine implemented with [Netty](https://netty.io/) and [netty-tcnative](https://github.com/netty/netty-tcnative) (BoringSSL)
- Modules implemented with [Jackson](https://github.com/FasterXML/jackson) | [swagger-core](https://github.com/swagger-api/swagger-core) | [Pebble](https://pebbletemplates.io/) | [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/) | [Nimbus JOSE + JWT](https://connect2id.com/products/nimbus-jose-jwt) | [Koin](https://insert-koin.io/) | [Brotli4j](https://github.com/hyperxpro/Brotli4j) | [zstd-jni](https://github.com/luben/zstd-jni)
- Monitored by [HTTP Arena](https://www.http-arena.com)
