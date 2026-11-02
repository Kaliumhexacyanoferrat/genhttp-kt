package org.genhttp.modules.mcp.provider

import java.util.jar.Attributes
import java.util.jar.JarFile

/**
 * Identifies the server to clients.
 */
internal data class ServerIdentity(val name: String, val version: String, val title: String?) {

    companion object {

        /**
         * Identifies the server by the application it is part of (the `Implementation-Title` and
         * `Implementation-Version` of the main class or jar, the entry assembly upstream).
         */
        fun default(): ServerIdentity {
            val (title, version) = findApplication()

            return ServerIdentity(title ?: "GenHTTP", version ?: "1.0.0", null)
        }

        private fun findApplication(): Pair<String?, String?> = runCatching {
            val command = System.getProperty("sun.java.command")?.substringBefore(' ')?.takeIf { it.isNotBlank() }
                ?: return@runCatching null to null

            if (command.endsWith(".jar", ignoreCase = true)) {
                JarFile(command).use { jar ->
                    val attributes = jar.manifest?.mainAttributes

                    attributes?.getValue(Attributes.Name.IMPLEMENTATION_TITLE) to attributes?.getValue(Attributes.Name.IMPLEMENTATION_VERSION)
                }
            } else {
                val loader = Thread.currentThread().contextClassLoader ?: ServerIdentity::class.java.classLoader
                val main = Class.forName(command.substringAfter('/'), false, loader)

                main.`package`?.implementationTitle to main.`package`?.implementationVersion
            }
        }.getOrDefault(null to null)

    }

}
