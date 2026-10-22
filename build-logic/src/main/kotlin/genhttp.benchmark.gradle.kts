// Micro benchmarks with kotlinx-benchmark (JMH), which requires open state classes.

plugins {
    id("genhttp.kotlin")
    id("org.jetbrains.kotlin.plugin.allopen")
    id("org.jetbrains.kotlinx.benchmark")
}

allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}
