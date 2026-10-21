plugins {
    id("genhttp.library")
}

description = "Request inspection for debugging GenHTTP applications"

dependencies {
    api(project(":modules-conversion"))
    api(project(":modules-io"))
}
