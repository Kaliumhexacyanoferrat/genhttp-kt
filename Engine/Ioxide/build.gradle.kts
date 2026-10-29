plugins {
    id("genhttp.library")
}

description = "Web server engine for genhttp-kt on the io_uring reactors of ioxide (Linux only), the port of the ioxide engine of GenHTTP"

dependencies {
    api(project(":engine-shared"))
    api(project(":modules-io"))

    // the reactors are passed to the start hook of the engine (`Reactor`)
    api(project(":ioxide"))
}
