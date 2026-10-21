plugins {
    id("genhttp.library")
}

description = "Path based routing (layouts) for GenHTTP"

dependencies {
    api(project(":modules-redirects"))
}
