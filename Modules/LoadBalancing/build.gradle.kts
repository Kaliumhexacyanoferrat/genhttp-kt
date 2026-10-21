plugins {
    id("genhttp.library")
}

description = "Load balancing for GenHTTP"

dependencies {
    api(project(":modules-reverse-proxy"))
    api(project(":modules-redirects"))
}
