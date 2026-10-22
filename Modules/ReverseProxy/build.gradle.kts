plugins {
    id("genhttp.library")
}

description = "Reverse proxy for GenHTTP"

dependencies {
    api(project(":modules-io"))
    api(project(":modules-websockets"))
}
