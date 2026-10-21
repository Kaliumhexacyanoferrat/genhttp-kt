plugins {
    id("genhttp.library")
}

description = "Annotation based webservices (resources) for GenHTTP"

dependencies {
    api(project(":modules-reflection"))
    api(project(":modules-layouting"))
}
