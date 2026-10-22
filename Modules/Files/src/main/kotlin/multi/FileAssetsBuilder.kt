package org.genhttp.modules.files.multi

import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Concerns
import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.content.io.CompressionAlgorithm

import java.nio.file.Path

import kotlin.time.Duration

/**
 * Builds a handler that serves the files of a directory.
 *
 * @param directory The directory to be served
 */
public class FileAssetsBuilder(private val directory: Path) : HandlerBuilder.WithConcerns<FileAssetsBuilder> {

    private val concerns = mutableListOf<ConcernBuilder>()

    private val algorithms = mutableListOf<CompressionAlgorithm>()

    private var separator = '.'

    /**
     * Configures the handler to serve pre-compressed files that are placed next to the requested files.
     * If you pass the brotli algorithm to this method, the handler will look for a "file.txt.br" if
     * "file.txt" is requested.
     *
     * @param algorithms The supported algorithms for pre-compression
     */
    public fun allowPrecompressed(vararg algorithms: CompressionAlgorithm): FileAssetsBuilder {
        this.algorithms.addAll(algorithms)
        return this
    }

    /**
     * Configures the handler to serve pre-compressed files that are placed next to the requested files.
     * If you pass the brotli algorithm and "-" as a separator to this method, the handler will look for
     * a "file.txt-br" if "file.txt" is requested.
     *
     * @param algorithms The supported algorithms for pre-compression
     * @param separator The separator to use to build the paths
     */
    public fun allowPrecompressed(algorithms: Array<out CompressionAlgorithm>, separator: Char): FileAssetsBuilder {
        this.separator = separator

        return allowPrecompressed(*algorithms)
    }

    /**
     * How often the mounted directory may be re-scanned for changes. Defaults to 250 ms and
     * only affects the Ioxide engine of GenHTTP, so it has no effect on this server.
     *
     * @param interval The refresh interval ([Duration.INFINITE] to never refresh)
     */
    public fun refreshInterval(interval: Duration): FileAssetsBuilder {
        require(!interval.isNegative()) { "The refresh interval must not be negative." }
        return this
    }

    override fun add(concern: ConcernBuilder): FileAssetsBuilder {
        concerns.add(concern)
        return this
    }

    override fun build(): Handler = Concerns.chain(concerns, FileAssetsHandler(directory, algorithms, separator))

}
