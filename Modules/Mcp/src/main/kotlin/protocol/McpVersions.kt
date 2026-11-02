package org.genhttp.modules.mcp.protocol

import org.genhttp.api.protocol.ByteString

/**
 * The revisions of the protocol supported by this implementation.
 *
 * Modern revisions are stateless and carry their version with every request. Legacy revisions negotiate their
 * version with an initialize handshake, which this implementation answers without creating a session.
 */
internal object McpVersions {

    const val Modern: String = "2026-07-28"

    const val LatestLegacy: String = "2025-11-25"

    /**
     * Assumed for requests without a version header, as clients before 2025-06-18 did not send it.
     */
    const val Unspecified: String = "2025-03-26"

    val Supported: List<String> = listOf(Modern, LatestLegacy, "2025-06-18", Unspecified)

    private val Encoded: List<ByteString> = Supported.map { ByteString(it) }

    fun find(value: ByteString): String? {
        for (i in Encoded.indices) {
            if (Encoded[i] == value) {
                return Supported[i]
            }
        }

        return null
    }

    fun isModern(version: String): Boolean = version == Modern

    fun negotiate(requested: String?): String {
        if (requested != null) {
            for (i in 1 until Supported.size) {
                if (Supported[i] == requested) {
                    return Supported[i]
                }
            }
        }

        return LatestLegacy
    }

}
