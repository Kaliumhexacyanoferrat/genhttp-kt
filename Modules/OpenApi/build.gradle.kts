plugins {
    id("genhttp.library")
}

description = "Generates OpenAPI specifications for GenHTTP services"

dependencies {
    api(project(":modules-layouting"))
    api(project(":modules-reflection"))
    api(libs.swagger.core)
}
