// Convention for aggregate packages (genhttp-core, genhttp-full) that only bundle dependencies, like the
// GenHTTP.Core and GenHTTP.Full NuGet meta packages.

import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar
import java.util.Properties

plugins {
    `java-library`
    id("com.vanniktech.maven.publish")
}

val upstream = Properties().apply {
    rootProject.file("upstream.properties").reader().use { load(it) }
}

group = "org.genhttp"
version = "${upstream.getProperty("genhttp.version")}.${upstream.getProperty("port.revision")}"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

val artifact = (findProperty("genhttp.artifact") as String?) ?: "genhttp-${project.name}"

mavenPublishing {
    configure(JavaLibrary(javadocJar = JavadocJar.Empty(), sourcesJar = SourcesJar.Empty()))

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
