plugins {
    id("genhttp.library")
}

description = "Directory listings for resource trees for GenHTTP"

dependencies {
    api(project(":modules-io"))
    api(project(":modules-pages"))
}
