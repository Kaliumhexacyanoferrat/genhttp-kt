plugins {
    id("genhttp.library")
}

description = "Hosting of single page applications for GenHTTP"

dependencies {
    api(project(":modules-files"))
    api(project(":modules-io"))
}
