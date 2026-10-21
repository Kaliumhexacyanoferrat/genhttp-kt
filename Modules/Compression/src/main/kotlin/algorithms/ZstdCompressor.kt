package org.genhttp.modules.compression.algorithms

import com.github.luben.zstd.ZstdOutputStreamNoFinalizer

import org.genhttp.api.content.io.CompressionLevel

import java.io.OutputStream

internal class ZstdCompressor(level: CompressionLevel) : Compressor {

    private val quality = mapQuality(level)

    override fun wrap(target: OutputStream): OutputStream = ZstdOutputStreamNoFinalizer(target, quality)

    private companion object {

        private fun mapQuality(level: CompressionLevel): Int = when (level) {
            CompressionLevel.Fastest -> 1
            CompressionLevel.Optimal -> 3
            CompressionLevel.SmallestSize -> 19
            else -> throw IllegalArgumentException("Unsupported compression level: $level")
        }

    }

}
