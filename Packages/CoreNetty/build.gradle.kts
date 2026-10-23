plugins {
    id("genhttp.package")
}

description = "Basic dependencies for projects using the genhttp-kt framework, including the Netty engine"

dependencies {
    api(project(":api"))
    api(project(":engine-netty"))
    api(project(":modules-practices"))
    api(project(":modules-io"))
    api(project(":modules-layouting"))
}
