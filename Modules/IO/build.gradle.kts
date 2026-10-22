plugins {
    id("genhttp.library")
}

description = "Content and resource abstractions for the GenHTTP framework (strings, files, classpath and web resources, ranges)"

dependencies {
    api(project(":api"))
}
