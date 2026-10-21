package org.genhttp.modules.compression.algorithms

import com.aayushatharva.brotli4j.encoder.BrotliOutputStream
import com.aayushatharva.brotli4j.encoder.Encoder

import org.genhttp.api.content.io.CompressionLevel
import org.genhttp.api.infrastructure.BufferSize

import java.io.OutputStream

internal class BrotliCompressor(level: CompressionLevel) : Compressor {

    private val parameters = Encoder.Parameters()
        .setQuality(mapQuality(level))
        .setWindow(mapWindow(level))

    override fun wrap(target: OutputStream): OutputStream = BrotliOutputStream(target, parameters, BufferSize.Write)

    private companion object {

        private fun mapQuality(level: CompressionLevel): Int = when (level) {
            CompressionLevel.Fastest -> 0
            CompressionLevel.Optimal -> 4
            CompressionLevel.SmallestSize -> 11
            else -> throw IllegalArgumentException("Unsupported compression level: $level")
        }

        private fun mapWindow(level: CompressionLevel): Int = when (level) {
            CompressionLevel.Fastest -> 16
            CompressionLevel.Optimal -> 20
            CompressionLevel.SmallestSize -> 22
            else -> throw IllegalArgumentException("Unsupported compression level: $level")
        }

    }

}
