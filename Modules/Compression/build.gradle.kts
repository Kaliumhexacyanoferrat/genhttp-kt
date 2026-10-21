plugins {
    id("genhttp.library")
}

description = "Response compression (gzip, Brotli, Zstandard) and request decompression for GenHTTP"

dependencies {
    api(project(":modules-io"))
    api(libs.brotli4j)
    api(libs.zstd)

    // native libraries required by Brotli4j on the platforms supported by GenHTTP
    runtimeOnly(libs.brotli4j.native.linux.x64)
    runtimeOnly(libs.brotli4j.native.linux.arm64)
    runtimeOnly(libs.brotli4j.native.windows.x64)
    runtimeOnly(libs.brotli4j.native.windows.arm64)
    runtimeOnly(libs.brotli4j.native.osx.x64)
    runtimeOnly(libs.brotli4j.native.osx.arm64)
}
