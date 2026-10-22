package org.genhttp.modules.files.multi

import org.genhttp.api.content.io.CompressionAlgorithm
import org.genhttp.api.protocol.KnownHeaders
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.getEntry
import org.genhttp.modules.compression.providers.AcceptEncodingHeader

import java.nio.ByteBuffer

/**
 * Precompressed-variant negotiation used by the asset handlers. The sibling extension follows the
 * algorithm's [CompressionAlgorithm.fileExtension] (e.g. a `.gz` file), while the resulting
 * Content-Encoding follows the algorithm name (`gzip`).
 */
internal class PreCompression(algorithms: List<CompressionAlgorithm>, separator: Char) {

    private val algorithms: List<SupportedCompression> = algorithms
        .map { algorithm ->
            val suffix = algorithm.fileExtension.toByteArray(Charsets.US_ASCII)

            val extension = ByteArray(suffix.size + 1)

            extension[0] = separator.code.toByte()

            suffix.copyInto(extension, 1)

            SupportedCompression(algorithm, ByteBuffer.wrap(extension))
        }
        .sortedByDescending { it.algorithm.priority.value }

    /**
     * Whether any precompressed variant has been configured at all.
     */
    val enabled: Boolean
        get() = algorithms.isNotEmpty()

    /**
     * The configured variants the client accepts, best (highest priority) first.
     */
    fun accepted(request: Request): List<SupportedCompression> {
        val header = request.header.headers.getEntry(KnownHeaders.AcceptEncoding) ?: return emptyList()

        val requested = AcceptEncodingHeader.parseSupported(header)

        return algorithms.filter { requested.contains(it.algorithm.name) }
    }

}
