plugins {
    id("genhttp.library")
}

description = "Protobuf serialization for GenHTTP"

dependencies {
    api(project(":modules-conversion"))
    api(libs.serialization.protobuf)
}
