# Contributing

genhttp-kt is happy about all contributions such as bug fixes, improvements or additional features.

## How This Project Works

genhttp-kt is an automated port of [GenHTTP](https://github.com/Kaliumhexacyanoferrat/GenHTTP). GenHTTP is the
source of truth for the public API and the behavior of the server. This has some implications for contributions:

- **Features and behavioral changes** that are not specific to Kotlin or the JVM should be contributed to
  [GenHTTP](https://github.com/Kaliumhexacyanoferrat/GenHTTP) first. They will then be ported to genhttp-kt by the
  regular synchronization (see [SYNC.md](SYNC.md)).
- **Port-specific contributions** are welcome here, for example bugs that only exist in the Kotlin port, improvements
  of the Kotlin/JVM integration (coroutines, NIO, Gradle, Maven Central publishing) or better Kotlin idioms that do not
  change the API surface.

## Getting Started

To add your contributions, create a fork of this repository and add your changes there. As soon as your work has
finished, create a pull request to allow a maintainer to review and merge your changes into the `main` branch.

You will need JDK 25. The Gradle wrapper downloads everything else:

```sh
./gradlew build
```

## Definition of Done

For a pull request to be merged, the following rules need to be fulfilled:

- The automated build was able to compile and successfully test the changes on all platforms
- The test coverage does not drop below 90 %
- Public API declarations are documented (KDoc) and the ABI dumps (`api/*.api`) are updated (`./gradlew updateKotlinAbi`)
- `Handler` and `Concern` instances can be created using a builder
- The changes follow the rules in [PORTING.md](PORTING.md); deviations from GenHTTP are documented in its deviation log
- Architecture principles as described in the following section have not been violated

## Project Structure

genhttp-kt mirrors the structure of GenHTTP. The following table describes the projects and their purpose.

| Project                   | Description                                                                                                                                                                                       |
|---------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `genhttp-api`             | Public interface of the webserver. Contains types that are useful for all kind of content providers. Problem specific types and references to 3rd party libraries are not allowed here.         |
| `genhttp-engine-*`        | The actual implementation of the webserver. References to 3rd party libraries or complex modules are not allowed.                                                                               |
| `genhttp-modules-*`       | Additional modules that can be used to add functionality to the webserver. References to 3rd party libraries are allowed. Must not reference `genhttp-engine-*` to stay independent from the server implementation. May reference other modules as needed. |
| `glyph11-kt`              | The HTTP/1.1 parser used by the engine, ported from [Glyph11](https://github.com/dotnet-web-stack/Glyph11).                                                                                       |

## Legal Aspects

Contributing to genhttp-kt does currently not require you to sign a CLA. Nevertheless, please consider the following
aspects when contributing to the project:

### Code of Conduct

To maintain the integrity of the project, please do not submit contributions:

- That you do not own or have rights to.
- That violate the intellectual property of others.
- That might lead to legal disputes or damage the reputation of the project.

By contributing, you certify that your submission does not violate any of the above and is free from legal entanglements.

### Licensing Implications

Before contributing, please ensure you understand the implications of submitting code under the MIT license. This
means your code can be used, modified, and redistributed by anyone, including for commercial purposes, as long as the
MIT license terms are followed.

By submitting contributions, you agree to license them under the MIT license, as specified in this repository. This
means you cannot later revoke or modify the license for contributions you've already made.

### Ownership of Contributions

By contributing to this project, you confirm that you are the sole owner of the code you submit or have received
explicit permission from the rightful owner to contribute it. If your contributions are part of your employment, you
must ensure your employer has approved your contribution under the terms of this project's license.

### Patent Licensing

By submitting your contributions, you agree that you will not include any code that is subject to any patents or
patent claims that you own or control. You also agree not to contribute any code that might infringe on any existing
patents.

### License Compatibility

By submitting code, you confirm that it is your original work and that it has not been copied, in whole or in part,
from any other project, codebase, or source. You must not submit any code that is derived from or based on external
sources unless you have explicit permission to do so and can verify that the code is compatible with the project's MIT
license.

### Future License Changes

By contributing to this project, you acknowledge that the project maintainers may, at their discretion, relicense the
project under terms compatible with the original MIT license. Your contributions will remain under the same licensing
terms as the project.
