plugins {
    id("genhttp.library")
}

description = "Serves the content of archives (zip, tar, 7z, ...) as resource trees for GenHTTP"

dependencies {
    api(project(":modules-io"))
    api(libs.commons.compress)

    // LZMA support required to read 7z archives
    implementation(libs.xz)
}
