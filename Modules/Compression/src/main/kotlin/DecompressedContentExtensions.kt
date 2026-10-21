@file:JvmName("DecompressedContentExtensions")

package org.genhttp.modules.compression

import org.genhttp.api.infrastructure.ServerHost
import org.genhttp.modules.compression.providers.DecompressionConcernBuilder

/**
 * Configures the host to automatically decompress incoming request content
 * if it has been compressed by the client.
 *
 * @receiver The host to be configured
 * @param decompression The decompression builder to use
 * @return The configured server host for chaining
 */
public fun ServerHost.decompression(decompression: DecompressionConcernBuilder): ServerHost {
    add(decompression)
    return this
}

/**
 * Configures the host to automatically decompress incoming request content
 * if it has been compressed by the client.
 *
 * @receiver The host to be configured
 * @return The configured server host for chaining
 */
public fun ServerHost.decompression(): ServerHost = decompression(DecompressedContent.default())
