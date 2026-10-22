plugins {
    id("genhttp.library")
}

description = "Template based page rendering for GenHTTP"

dependencies {
    api(project(":modules-io"))
    api(libs.pebble)
}
