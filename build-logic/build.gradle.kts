plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.gradle.kotlin)
    implementation(libs.gradle.kover)
    implementation(libs.gradle.dokka)
    implementation(libs.gradle.maven.publish)
    implementation(libs.gradle.serialization)
    implementation(libs.gradle.allopen)
    implementation(libs.gradle.benchmark)
}

dependencies {
    // makes the type-safe version catalog accessors available to the convention plugins
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
