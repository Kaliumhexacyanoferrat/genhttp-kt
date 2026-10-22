plugins {
    id("genhttp.library")
}

description = "Shared library that can be used by server engines to be integrated into GenHTTP"

dependencies {
    api(project(":api"))
    api(project(":glyph11"))
    api(project(":modules-error-handling"))
}
