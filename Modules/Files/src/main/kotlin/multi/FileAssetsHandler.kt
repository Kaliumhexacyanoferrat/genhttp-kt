package org.genhttp.modules.files.multi

import org.genhttp.api.content.Handler
import org.genhttp.api.content.io.CompressionAlgorithm
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response

import java.nio.file.Path

/**
 * A handler that serves the files of a directory.
 *
 * @param directory The directory to be served
 * @param algorithms The algorithms to look for pre-compressed variants of
 * @param separator The separator between the file name and the extension of the compressed variant
 */
public class FileAssetsHandler(
    private val directory: Path,
    private val algorithms: List<CompressionAlgorithm>,
    private val separator: Char
) : Handler {

    private var inner: Handler? = null

    private val delegate: Handler
        get() = inner ?: throw IllegalStateException("Handler has not been prepared")

    override suspend fun prepare(server: Server) {
        val handler = BuiltInFileAssetHandler(directory, algorithms, separator)

        inner = handler

        handler.prepare(server)
    }

    override suspend fun handle(request: Request): Response? = delegate.handle(request)

}
