plugins {
    id("genhttp.library")
}

description = "Authentication concerns (basic, API key, bearer/JWT, client certificates) for GenHTTP"

dependencies {
    api(project(":modules-reflection"))
    api(libs.nimbus.jose)
}
