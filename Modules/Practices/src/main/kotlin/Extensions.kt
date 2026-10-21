@file:JvmName("Extensions")

package org.genhttp.modules.practices

import org.genhttp.api.infrastructure.ServerHost
import org.genhttp.modules.clientcaching.clientCaching
import org.genhttp.modules.compression.CompressedContent
import org.genhttp.modules.compression.DecompressedContent
import org.genhttp.modules.compression.compression
import org.genhttp.modules.compression.decompression
import org.genhttp.modules.io.rangeSupport
import org.genhttp.modules.security.SecureUpgrade
import org.genhttp.modules.security.preventSniffing
import org.genhttp.modules.security.providers.StrictTransportPolicy
import org.genhttp.modules.security.secureUpgrade
import org.genhttp.modules.security.strictTransport

import kotlin.time.Duration.Companion.days

/**
 * Configures the server host with default policies for compression and security.
 *
 * @param compression Whether responses sent by the server should automatically be compressed
 * @param decompression Whether incoming request content should automatically be decompressed
 * @param secureUpgrade Whether the server should automatically upgrade insecure requests
 * @param strictTransport Whether the server should send a strict transport policy
 * @param clientCaching Validates the cached entries of the client by sending an ETag header and evaluating it when
 * a request is processed (returning HTTP 304 if the content did not change)
 * @param rangeSupport Enables partial responses if requested by the client
 * @param preventSniffing Instructs clients not to guess the MIME type of the served content
 */
public fun ServerHost.defaults(
    compression: Boolean = true,
    decompression: Boolean = false,
    secureUpgrade: Boolean = true,
    strictTransport: Boolean = true,
    clientCaching: Boolean = true,
    rangeSupport: Boolean = false,
    preventSniffing: Boolean = false
): ServerHost {
    if (strictTransport) {
        strictTransport(StrictTransportPolicy(365.days, true, true))
    }

    if (compression) {
        compression(CompressedContent.default())
    }

    if (decompression) {
        decompression(DecompressedContent.default())
    }

    if (rangeSupport) {
        rangeSupport()
    }

    if (clientCaching) {
        clientCaching()
    }

    if (secureUpgrade) {
        secureUpgrade(SecureUpgrade.Force)
    }

    if (preventSniffing) {
        preventSniffing()
    }

    return this
}
