package org.genhttp.modules.compression.algorithms

import com.github.luben.zstd.ZstdInputStreamNoFinalizer

import org.genhttp.api.content.io.AlgorithmName
import org.genhttp.api.content.io.CompressionAlgorithm
import org.genhttp.api.content.io.CompressionLevel
import org.genhttp.api.infrastructure.Priority
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.modules.compression.providers.CompressedResponseContent

import java.io.InputStream

/**
 * Compresses and decompresses content using the Zstandard algorithm ("zstd").
 */
public class ZstdAlgorithm : CompressionAlgorithm {

    override val name: AlgorithmName
        get() = CachedName

    override val fileExtension: String
        get() = "zst"

    override val priority: Priority
        get() = Priority.High

    override fun compress(content: ResponseContent, level: CompressionLevel): ResponseContent =
        CompressedResponseContent(content, { sink -> CompressingSink(sink, ZstdCompressor(level)) }, name)

    override fun decompress(content: InputStream): InputStream = decompressing(content) { ZstdInputStreamNoFinalizer(it) }

    private companion object {

        private val CachedName = AlgorithmName("zstd")

    }

}
