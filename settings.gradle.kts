pluginManagement {
    includeBuild("build-logic")
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "genhttp-kt"

/**
 * Registers a project whose directory mirrors the GenHTTP folder structure.
 */
fun module(name: String, directory: String) {
    include(":$name")
    project(":$name").projectDir = file(directory)
}

module("glyph11", "Glyph11")
module("api", "API/General")
module("modules-io", "Modules/IO")
module("modules-pages", "Modules/Pages")
module("modules-conversion", "Modules/Conversion")
module("modules-error-handling", "Modules/ErrorHandling")
module("modules-redirects", "Modules/Redirects")
module("modules-layouting", "Modules/Layouting")
module("modules-security", "Modules/Security")
module("modules-client-caching", "Modules/ClientCaching")
module("modules-compression", "Modules/Compression")
module("modules-practices", "Modules/Practices")
module("modules-protobuf", "Modules/Protobuf")
module("engine-shared", "Engine/Shared")
module("engine-internal", "Engine/Internal")
module("testing", "Testing/Testing")
module("testing-acceptance", "Testing/Acceptance")
