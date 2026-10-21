package org.genhttp.modules.compression.algorithms

import com.aayushatharva.brotli4j.Brotli4jLoader
import com.aayushatharva.brotli4j.decoder.BrotliInputStream

import org.genhttp.api.content.io.AlgorithmName
import org.genhttp.api.content.io.CompressionAlgorithm
import org.genhttp.api.content.io.CompressionLevel
import org.genhttp.api.infrastructure.Priority
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.modules.compression.providers.CompressedResponseContent

import java.io.InputStream

/**
 * Compresses and decompresses content using the Brotli algorithm ("br").
 */
public class BrotliAlgorithm : CompressionAlgorithm {

    override val name: AlgorithmName
        get() = CachedName

    override val fileExtension: String
        get() = "br"

    override val priority: Priority
        get() = Priority.Medium

    override fun compress(content: ResponseContent, level: CompressionLevel): ResponseContent {
        ensureNativesLoaded()

        return CompressedResponseContent(content, { sink -> CompressingSink(sink, BrotliCompressor(level)) }, name)
    }

    override fun decompress(content: InputStream): InputStream {
        ensureNativesLoaded()

        return decompressing(content) { BrotliInputStream(it) }
    }

    private companion object {

        private val CachedName = AlgorithmName("br")

        private val natives: Unit by lazy { Brotli4jLoader.ensureAvailability() }

        private fun ensureNativesLoaded() = natives

    }

}
