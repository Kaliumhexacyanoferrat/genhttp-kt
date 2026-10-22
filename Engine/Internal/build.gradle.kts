plugins {
    id("genhttp.library")
}

description = "Lightweight web server engine written in pure Kotlin based on NIO, virtual threads and coroutines"

dependencies {
    api(project(":engine-shared"))
    api(project(":modules-io"))
}
