plugins {
    id("genhttp.library")
}

description = "Graphical API browsers (Swagger UI, Redoc, Scalar) for GenHTTP"

dependencies {
    api(project(":modules-files"))
    api(project(":modules-io"))
    api(project(":modules-layouting"))
    api(project(":modules-pages"))
}
