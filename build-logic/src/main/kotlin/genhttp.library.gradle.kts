// Convention for published library projects (API, engine, modules, testing, glyph11).

plugins {
    id("genhttp.kotlin")
    id("org.jetbrains.dokka")
    id("com.vanniktech.maven.publish")
}

kotlin {
    explicitApi()

    @OptIn(org.jetbrains.kotlin.gradle.dsl.abi.ExperimentalAbiValidation::class)
    abiValidation()
}

val artifact = (findProperty("genhttp.artifact") as String?) ?: "genhttp-${project.name}"

mavenPublishing {
    publishToMavenCentral(automaticRelease = false)

    if (providers.gradleProperty("signingInMemoryKey").isPresent) {
        signAllPublications()
    }

    coordinates("org.genhttp", artifact, project.version.toString())

    pom {
        name.set(artifact)
        description.set(provider { project.description ?: artifact })
        url.set("https://github.com/Kaliumhexacyanoferrat/genhttp-kt")
        inceptionYear.set("2026")

        licenses {
            license {
                name.set("MIT License")
                url.set("https://opensource.org/licenses/MIT")
            }
        }

        developers {
            developer {
                id.set("Kaliumhexacyanoferrat")
                name.set("Andreas Nägeli")
                url.set("https://github.com/Kaliumhexacyanoferrat")
            }
        }

        scm {
            url.set("https://github.com/Kaliumhexacyanoferrat/genhttp-kt")
            connection.set("scm:git:https://github.com/Kaliumhexacyanoferrat/genhttp-kt.git")
            developerConnection.set("scm:git:ssh://git@github.com/Kaliumhexacyanoferrat/genhttp-kt.git")
        }
    }
}
