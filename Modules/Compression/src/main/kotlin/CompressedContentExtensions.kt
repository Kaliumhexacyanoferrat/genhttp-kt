@file:JvmName("CompressedContentExtensions")

package org.genhttp.modules.compression

import org.genhttp.api.infrastructure.ServerHost
import org.genhttp.modules.compression.providers.CompressionConcernBuilder

/**
 * Configures the host to compress responses if the client supports
 * this feature.
 *
 * @receiver The host to be configured
 * @param compression The compression builder to use for compression
 * @return The configured server host for chaining
 */
public fun ServerHost.compression(compression: CompressionConcernBuilder): ServerHost {
    add(compression)
    return this
}

/**
 * Configures the host to compress responses if the client supports
 * this feature.
 *
 * @receiver The host to be configured
 * @return The configured server host for chaining
 */
public fun ServerHost.compression(): ServerHost = compression(CompressedContent.default())
