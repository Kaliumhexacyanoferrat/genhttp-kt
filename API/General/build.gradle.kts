plugins {
    id("genhttp.library")
}

description = "Public API of the GenHTTP webserver. This package is at least required to build a project that can be executed with the GenHTTP webserver."

dependencies {
    api(libs.coroutines.core)
    api(libs.slf4j.api)
}
