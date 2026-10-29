plugins {
    id("genhttp.library")
}

description = "The io_uring runtime of the ioxide engine, ioxide's own Kotlin port (github.com/MDA2AV/ioxide, kotlin/ioxide)"

kotlin {
    // the sources are kept as upstream publishes them (see upstream.properties), upstream does not use the explicit API mode
    explicitApi = org.jetbrains.kotlin.gradle.dsl.ExplicitApiMode.Disabled
}
