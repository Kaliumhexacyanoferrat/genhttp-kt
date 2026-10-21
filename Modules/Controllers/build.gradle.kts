plugins {
    id("genhttp.library")
}

description = "Convention based controllers for GenHTTP"

dependencies {
    api(project(":modules-reflection"))
    api(project(":modules-layouting"))
}
