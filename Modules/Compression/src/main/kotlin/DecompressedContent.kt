package org.genhttp.modules.compression

import org.genhttp.modules.compression.algorithms.BrotliAlgorithm
import org.genhttp.modules.compression.algorithms.GzipAlgorithm
import org.genhttp.modules.compression.algorithms.ZstdAlgorithm
import org.genhttp.modules.compression.providers.DecompressionConcernBuilder

/**
 * Provides automatic decompression of incoming request content.
 */
public object DecompressedContent {

    // region Builder

    /**
     * Creates an empty builder that can be configured by adding
     * decompression algorithms.
     *
     * @return The newly created builder
     */
    public fun empty(): DecompressionConcernBuilder = DecompressionConcernBuilder()

    /**
     * Creates a pre-configured builder which already supports
     * Brotli, Gzip and Zstandard decompression.
     *
     * @return The newly created builder
     */
    public fun default(): DecompressionConcernBuilder = DecompressionConcernBuilder()
        .add(BrotliAlgorithm())
        .add(GzipAlgorithm())
        .add(ZstdAlgorithm())

    // endregion

}
