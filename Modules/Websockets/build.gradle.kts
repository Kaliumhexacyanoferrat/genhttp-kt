plugins {
    id("genhttp.library")
}

description = "Websocket support for GenHTTP"

dependencies {
    api(project(":modules-conversion"))
}
