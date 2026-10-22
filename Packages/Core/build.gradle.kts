plugins {
    id("genhttp.package")
}

description = "Basic dependencies for projects using the genhttp-kt framework, including the engine itself"

dependencies {
    api(project(":api"))
    api(project(":engine-internal"))
    api(project(":modules-practices"))
    api(project(":modules-io"))
    api(project(":modules-layouting"))
}
