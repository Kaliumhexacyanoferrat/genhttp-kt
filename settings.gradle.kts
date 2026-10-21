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
