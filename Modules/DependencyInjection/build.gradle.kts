plugins {
    id("genhttp.library")
}

description = "Dependency injection (Koin) for GenHTTP"

dependencies {
    api(project(":modules-controllers"))
    api(project(":modules-conversion"))
    api(project(":modules-functional"))
    api(project(":modules-layouting"))
    api(project(":modules-reflection"))
    api(project(":modules-webservices"))
    api(project(":modules-websockets"))
    api(libs.koin.core)
}
