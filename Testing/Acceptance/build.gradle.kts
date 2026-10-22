plugins {
    id("genhttp.kotlin")
    id("genhttp.serialization")
}

description = "Acceptance tests of genhttp-kt"

dependencies {
    testImplementation(project(":testing"))
    testImplementation(project(":engine-shared"))
    testImplementation(project(":modules-io"))
    testImplementation(project(":modules-layouting"))
    testImplementation(project(":modules-redirects"))
    testImplementation(project(":modules-error-handling"))
    testImplementation(project(":modules-pages"))
    testImplementation(project(":modules-conversion"))
    testImplementation(project(":modules-protobuf"))
    testImplementation(project(":modules-compression"))
    testImplementation(project(":modules-security"))
    testImplementation(project(":modules-client-caching"))
    testImplementation(project(":modules-practices"))
    testImplementation(project(":modules-reflection"))
    testImplementation(project(":modules-functional"))
    testImplementation(project(":modules-webservices"))
    testImplementation(project(":modules-controllers"))
    testImplementation(libs.coroutines.test)
}

// tests that depend on modules which have not been ported yet (see STATUS.md)
val pendingTests = listOf(
    "modules/io/PathTraversalTests.kt",
    "modules/io/RangeTests.kt",
    "modules/io/VirtualTreeTests.kt",
    "modules/io/WebResourceTests.kt",
    "modules/layouting/LayoutTests.kt",
    "modules/reflection/WildcardTests.kt",
    "modules/webservices/HandlerResultTests.kt"
)

sourceSets.test {
    kotlin.exclude(pendingTests)
}

tasks.test {
    // the suite binds many ports and servers, run test classes one after another like upstream
    maxParallelForks = 1
    jvmArgs(
        "-Djdk.httpclient.allowRestrictedHeaders=host,connection,content-length,expect,upgrade",
        "-Djdk.internal.httpclient.disableHostnameVerification=true",
        "--enable-native-access=ALL-UNNAMED"
    )
}
