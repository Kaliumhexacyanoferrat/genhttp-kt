plugins {
    id("genhttp.library")
}

description = "Best practices for GenHTTP servers (compression, client caching, security, range support)"

dependencies {
    api(project(":modules-compression"))
    api(project(":modules-security"))
    api(project(":modules-client-caching"))
}
