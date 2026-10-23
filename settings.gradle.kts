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
module("modules-reflection", "Modules/Reflection")
module("modules-functional", "Modules/Functional")
module("modules-webservices", "Modules/Webservices")
module("modules-controllers", "Modules/Controllers")
module("modules-files", "Modules/Files")
module("modules-archives", "Modules/Archives")
module("modules-authentication", "Modules/Authentication")
module("modules-directory-browsing", "Modules/DirectoryBrowsing")
module("modules-i18n", "Modules/I18n")
module("modules-inspection", "Modules/Inspection")
module("modules-open-api", "Modules/OpenApi")
module("modules-server-sent-events", "Modules/ServerSentEvents")
module("modules-virtual-hosting", "Modules/VirtualHosting")
module("modules-websockets", "Modules/Websockets")
module("modules-api-browsing", "Modules/ApiBrowsing")
module("modules-single-page-applications", "Modules/SinglePageApplications")
module("modules-static-websites", "Modules/StaticWebsites")
module("modules-reverse-proxy", "Modules/ReverseProxy")
module("modules-dependency-injection", "Modules/DependencyInjection")
module("modules-load-balancing", "Modules/LoadBalancing")
module("engine-shared", "Engine/Shared")
module("engine-internal", "Engine/Internal")
module("engine-netty", "Engine/Netty")
module("testing", "Testing/Testing")
module("testing-acceptance", "Testing/Acceptance")
module("core", "Packages/Core")
module("full", "Packages/Full")
module("core-netty", "Packages/CoreNetty")
module("full-netty", "Packages/FullNetty")
module("benchmarks", "Benchmarks")
module("playground", "Playground")
