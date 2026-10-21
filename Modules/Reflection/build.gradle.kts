plugins {
    id("genhttp.library")
}

description = "Shared reflection infrastructure for the GenHTTP service frameworks (webservices, controllers, inline handlers)"

dependencies {
    api(project(":modules-conversion"))
    api(libs.kotlin.reflect)
}
