plugins {
    id("genhttp.library")
}

description = "Static file serving with assets, resource trees and pre-compression for GenHTTP"

dependencies {
    api(project(":modules-io"))
    api(project(":modules-compression"))
}
