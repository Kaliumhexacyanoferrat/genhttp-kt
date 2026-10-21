plugins {
    id("genhttp.library")
}

description = "Customizable error pages and structured error responses for GenHTTP"

dependencies {
    api(project(":modules-conversion"))
    api(project(":modules-pages"))
}
