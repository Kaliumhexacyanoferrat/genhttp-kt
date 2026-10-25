plugins {
    id("genhttp.library")
}

description = "Testing infrastructure for applications and modules built with GenHTTP"

dependencies {
    api(project(":engine-internal"))
    api(project(":engine-netty"))
    api(project(":engine-ioxide"))
    api(project(":modules-practices"))
    api(project(":modules-conversion"))
    api(project(":modules-protobuf"))
}
