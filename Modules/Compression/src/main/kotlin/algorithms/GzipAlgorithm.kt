package org.genhttp.modules.compression.algorithms

import org.genhttp.api.content.io.AlgorithmName
import org.genhttp.api.content.io.CompressionAlgorithm
import org.genhttp.api.content.io.CompressionLevel
import org.genhttp.api.infrastructure.Priority
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.modules.compression.providers.CompressedResponseContent
import org.genhttp.modules.compression.providers.StreamCompressingSink

import java.io.InputStream
import java.io.OutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Compresses and decompresses content using the gzip algorithm ("gzip").
 */
public class GzipAlgorithm : CompressionAlgorithm {

    override val name: AlgorithmName
        get() = CachedName

    override val fileExtension: String
        get() = "gz"

    override val priority: Priority
        get() = Priority.Low

    override fun compress(content: ResponseContent, level: CompressionLevel): ResponseContent {
        val deflateLevel = mapLevel(level)

        return CompressedResponseContent(content, { sink -> StreamCompressingSink(sink) { target -> LeveledGZIPOutputStream(target, deflateLevel) } }, name)
    }

    override fun decompress(content: InputStream): InputStream = decompressing(content) { GZIPInputStream(it) }

    /**
     * A gzip stream with a configurable level that flushes the compressor (and the target) when flushed,
     * so streamed content such as server sent events reaches the client.
     */
    private class LeveledGZIPOutputStream(target: OutputStream, level: Int) : GZIPOutputStream(target, 8192, true) {

        init {
            def.setLevel(level)
        }

    }

    private companion object {

        private val CachedName = AlgorithmName("gzip")

        private fun mapLevel(level: CompressionLevel): Int = when (level) {
            CompressionLevel.Optimal -> 6
            CompressionLevel.Fastest -> 1
            CompressionLevel.NoCompression -> 0
            CompressionLevel.SmallestSize -> 9
        }

    }

}
