plugins {
    id("genhttp.library")
}

description = "Security concerns for GenHTTP (CORS, HSTS, secure upgrade, sniffer prevention)"

dependencies {
    api(project(":modules-redirects"))
}
