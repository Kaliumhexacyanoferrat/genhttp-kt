package org.genhttp.modules.compression.providers

import org.genhttp.api.content.Concern
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.api.content.io.CompressionAlgorithm
import org.genhttp.api.infrastructure.Builder

/**
 * Builder for configuring automatic request content decompression.
 */
public class DecompressionConcernBuilder : ConcernBuilder {

    private val algorithms = mutableListOf<CompressionAlgorithm>()

    /**
     * Adds a decompression algorithm built from a builder.
     *
     * @param algorithm The algorithm builder to add
     * @return The builder instance for method chaining
     */
    public fun add(algorithm: Builder<CompressionAlgorithm>): DecompressionConcernBuilder = add(algorithm.build())

    /**
     * Adds a decompression algorithm.
     *
     * @param algorithm The algorithm to add
     * @return The builder instance for method chaining
     */
    public fun add(algorithm: CompressionAlgorithm): DecompressionConcernBuilder {
        algorithms.add(algorithm)
        return this
    }

    /**
     * Builds the decompression concern with the configured algorithms.
     *
     * @param content The handler to wrap
     * @return The configured decompression concern
     */
    override fun build(content: Handler): Concern {
        val algorithms = algorithms.associateBy { it.name }

        return DecompressionConcern(content, algorithms)
    }

}
