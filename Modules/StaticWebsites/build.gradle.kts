plugins {
    id("genhttp.library")
}

description = "Hosting of static websites for GenHTTP"

dependencies {
    api(project(":modules-files"))
    api(project(":modules-io"))
}
