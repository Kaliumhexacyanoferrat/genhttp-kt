plugins {
    id("genhttp.library")
}

description = "Event loop based web server engine for genhttp-kt, built on Netty (the counterpart of the ioxide engine)"

dependencies {
    api(project(":engine-shared"))
    api(project(":modules-io"))

    api(platform(libs.netty.bom))

    // the event loops are passed to the start hook of the engine (`EventLoop`)
    api(libs.netty.transport)

    implementation(libs.netty.handler)
    implementation(libs.netty.epoll)
    implementation(libs.netty.iouring)

    // native transports for the platforms supported by GenHTTP
    for (platform in listOf("linux-x86_64", "linux-aarch_64")) {
        runtimeOnly(variantOf(libs.netty.epoll.native) { classifier(platform) })
        runtimeOnly(variantOf(libs.netty.iouring.native) { classifier(platform) })
    }

    // OpenSSL (BoringSSL) for TLS, the JDK provider is used on other platforms
    runtimeOnly(libs.netty.tcnative.classes)

    for (platform in listOf("linux-x86_64", "linux-aarch_64", "osx-x86_64", "osx-aarch_64", "windows-x86_64")) {
        runtimeOnly(variantOf(libs.netty.tcnative.boringssl) { classifier(platform) })
    }
}
