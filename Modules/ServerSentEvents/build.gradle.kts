plugins {
    id("genhttp.library")
}

description = "Server sent events for GenHTTP"

dependencies {
    api(project(":modules-conversion"))
}
