package org.genhttp.modules.mcp.provider

import java.net.URI

/**
 * Decides which web pages may use the endpoint from a browser.
 *
 * Agents do not send an origin and are always accepted. A browser always does, and without this check a page of
 * any site could drive the endpoint, including via DNS rebinding, which is why comparing the origin with the
 * host does not suffice.
 */
internal class OriginPolicy(allowed: Iterable<String>) {

    private val allowed: Set<String> = allowed.mapTo(HashSet()) { origin ->
        normalize(origin) ?: throw IllegalArgumentException("'$origin' is not a valid origin (such as 'https://example.com').")
    }

    fun allows(origin: String): Boolean {
        val normalized = normalize(origin)

        return normalized != null && normalized in allowed
    }

    private fun normalize(origin: String): String? {
        val uri = try {
            URI(origin)
        } catch (_: Exception) {
            return null
        }

        val scheme = uri.scheme?.lowercase() ?: return null

        if (!uri.isAbsolute || (scheme != "http" && scheme != "https")) {
            return null
        }

        val host = uri.host?.lowercase() ?: return null

        val port = if (uri.port >= 0) uri.port else if (scheme == "https") 443 else 80

        return "$scheme://$host:$port"
    }

}
