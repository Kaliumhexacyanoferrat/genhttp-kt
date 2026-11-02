plugins {
    id("genhttp.library")
}

description = "Provides tools to AI agents such as Claude via the Model Context Protocol (MCP)"

dependencies {
    api(project(":modules-reflection"))
    api(libs.jakarta.validation)
}
