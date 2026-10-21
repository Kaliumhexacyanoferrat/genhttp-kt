package org.genhttp.modules.compression.providers

import org.genhttp.api.content.Concern
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.api.content.io.CompressionAlgorithm
import org.genhttp.api.content.io.CompressionLevel
import org.genhttp.api.infrastructure.Builder

/**
 * Creates the concern compressing the responses of the content it is added to.
 */
public class CompressionConcernBuilder : ConcernBuilder {

    private val algorithms = mutableListOf<CompressionAlgorithm>()

    private var level = CompressionLevel.Fastest

    private var minimumSize: Long? = 256

    /**
     * Adds a compression algorithm built from a builder.
     *
     * @param algorithm The algorithm builder to add
     * @return The builder instance for method chaining
     */
    public fun add(algorithm: Builder<CompressionAlgorithm>): CompressionConcernBuilder = add(algorithm.build())

    /**
     * Adds a compression algorithm.
     *
     * @param algorithm The algorithm to add
     * @return The builder instance for method chaining
     */
    public fun add(algorithm: CompressionAlgorithm): CompressionConcernBuilder {
        algorithms.add(algorithm)
        return this
    }

    /**
     * Sets the level of compression to be applied (defaults to [CompressionLevel.Fastest]).
     *
     * @param level The level of compression to be applied
     * @return The builder instance for method chaining
     */
    public fun level(level: CompressionLevel): CompressionConcernBuilder {
        this.level = level
        return this
    }

    /**
     * Sets the minimum size threshold for compression. Content smaller than this
     * threshold will not be compressed to avoid unnecessary overhead.
     *
     * @param minimumSize The minimum size in bytes (defaults to 256). Set to null to disable the threshold.
     * @return The builder instance for method chaining
     */
    public fun minimumSize(minimumSize: Long?): CompressionConcernBuilder {
        this.minimumSize = minimumSize
        return this
    }

    override fun build(content: Handler): Concern = CompressionConcern(content, algorithms, level, minimumSize)

}
