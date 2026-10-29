plugins {
    id("genhttp.package")
}

description = "Basic dependencies for projects using the genhttp-kt framework, including the ioxide engine (Linux only)"

dependencies {
    api(project(":api"))
    api(project(":engine-ioxide"))
    api(project(":modules-practices"))
    api(project(":modules-io"))
    api(project(":modules-layouting"))
}
