plugins {
    id("genhttp.library")
}

description = "Client side caching policies and ETag validation for GenHTTP"

dependencies {
    api(project(":modules-io"))
}
