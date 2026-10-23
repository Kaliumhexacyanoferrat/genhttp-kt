plugins {
    alias(libs.plugins.kover)
}

dependencies {
    // aggregates the coverage of all projects (most of it produced by the acceptance tests)
    subprojects.filter { it.path !in setOf(":core", ":full", ":core-netty", ":full-netty", ":playground", ":benchmarks") }.forEach {
        kover(project(it.path))
    }
}

kover {
    reports {
        filters {
            excludes {
                // generated overloads for inline lambdas
                classes("org.genhttp.modules.functional.InlineBuilderExtensions")
            }
        }

        verify {
            rule {
                minBound(90)
            }
        }
    }
}
