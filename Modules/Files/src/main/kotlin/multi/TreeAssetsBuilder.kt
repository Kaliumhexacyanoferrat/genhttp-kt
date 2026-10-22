package org.genhttp.modules.files.multi

import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Concerns
import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.content.io.CompressionAlgorithm
import org.genhttp.api.content.io.ResourceTree

/**
 * Builds a handler that serves the resources of a resource tree.
 *
 * @param tree The tree to be served
 */
public class TreeAssetsBuilder(private val tree: ResourceTree) : HandlerBuilder.WithConcerns<TreeAssetsBuilder> {

    private val concerns = mutableListOf<ConcernBuilder>()

    private val algorithms = mutableListOf<CompressionAlgorithm>()

    private var separator = '.'

    /**
     * Configures the handler to serve pre-compressed resources that are placed next to the requested
     * resources (e.g. "file.txt.br" if "file.txt" is requested).
     *
     * @param algorithms The supported algorithms for pre-compression
     */
    public fun allowPrecompressed(vararg algorithms: CompressionAlgorithm): TreeAssetsBuilder {
        this.algorithms.addAll(algorithms)
        return this
    }

    /**
     * Configures the handler to serve pre-compressed resources that are placed next to the requested
     * resources, using the given separator (e.g. "file.txt-br" for "-" if "file.txt" is requested).
     *
     * @param algorithms The supported algorithms for pre-compression
     * @param separator The separator to use to build the paths
     */
    public fun allowPrecompressed(algorithms: Array<out CompressionAlgorithm>, separator: Char): TreeAssetsBuilder {
        this.separator = separator

        return allowPrecompressed(*algorithms)
    }

    override fun add(concern: ConcernBuilder): TreeAssetsBuilder {
        concerns.add(concern)
        return this
    }

    override fun build(): Handler = Concerns.chain(concerns, TreeAssetsHandler(tree, algorithms, separator))

}
