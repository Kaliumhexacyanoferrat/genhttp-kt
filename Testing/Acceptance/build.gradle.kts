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
    testImplementation(libs.coroutines.test)
}

// tests that depend on modules which have not been ported yet (see STATUS.md)
val pendingTests = listOf(
    "engine/ChunkedContentTest.kt",
    "engine/ClientConnectionTests.kt",
    "engine/compliance/ComplianceTests.kt",
    "engine/ConcernTests.kt",
    "engine/ConnectionTests.kt",
    "engine/MethodTests.kt",
    "engine/RequestPropertyTest.kt",
    "engine/SecurityTests.kt",
    "modules/compression/GeneratedContentTests.kt",
    "modules/conversion/ErrorHandlingTests.kt",
    "modules/conversion/SerializerTests.kt",
    "modules/errorhandling/CustomErrorMapperTests.kt",
    "modules/errorhandling/HtmlErrorMapperTest.kt",
    "modules/errorhandling/StructuredErrorMapperTests.kt",
    "modules/protobuf/ProtobufTests.kt"
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
