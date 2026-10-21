plugins {
    id("genhttp.library")
}

description = "Serialization and formatting of values for GenHTTP (JSON, XML, YAML, forms)"

dependencies {
    api(project(":modules-io"))
    api(libs.jackson.databind)
    api(libs.jackson.kotlin)
    api(libs.jackson.xml)
    api(libs.jackson.yaml)
    api(platform(libs.jackson.bom))
    api(libs.kotlin.reflect)
}
