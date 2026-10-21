plugins {
    id("genhttp.library")
}

description = "Functional (inline) web services for GenHTTP, defined via lambdas or function references"

dependencies {
    api(project(":modules-reflection"))
}
