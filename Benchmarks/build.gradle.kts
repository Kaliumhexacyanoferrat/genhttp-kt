plugins {
    id("genhttp.benchmark")
}

description = "Micro benchmarks of the request pipeline (port of GenHTTP.Benchmarks)"

dependencies {
    implementation(project(":engine-internal"))
    implementation(project(":engine-netty"))
    implementation(project(":modules-compression"))
    implementation(project(":modules-files"))
    implementation(project(":modules-functional"))
    implementation(project(":modules-webservices"))
    implementation(project(":modules-websockets"))
    implementation(libs.kotlinx.benchmark.runtime)
}

benchmark {
    targets {
        register("main")
    }

    configurations {
        named("main") {
            warmups = 3
            iterations = 5
            iterationTime = 1
            iterationTimeUnit = "s"
            mode = "avgt"
            outputTimeUnit = "us"
            advanced("jvmForks", 1)
        }

        // quick run of a single benchmark (like `BenchmarkRunner.Run<BaselineBenchmark>()` in GenHTTP)
        register("baseline") {
            include("BaselineBenchmark")
            warmups = 3
            iterations = 5
            iterationTime = 1
            iterationTimeUnit = "s"
            mode = "avgt"
            outputTimeUnit = "us"
        }
    }
}

// access to the connection internals of the engine, like the upstream benchmarks
tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin") {
    for (friend in listOf(":engine-internal", ":engine-shared")) {
        val output = project(friend).layout.buildDirectory

        friendPaths.from(output.dir("classes/kotlin/main"))
        friendPaths.from(output.dir("libs").map { dir -> dir.asFileTree.matching { include("*.jar") } })
    }
}
