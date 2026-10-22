plugins {
    id("genhttp.kotlin")
    application
}

description = "Samples showing how to use genhttp-kt"

dependencies {
    implementation(project(":full"))
}

application {
    mainClass.set("org.genhttp.playground.ProgramKt")
    applicationDefaultJvmArgs = listOf("--enable-native-access=ALL-UNNAMED")
}
