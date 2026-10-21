package org.genhttp.api.content.io

import org.genhttp.api.infrastructure.Priority
import org.genhttp.api.protocol.ResponseContent

import java.io.InputStream

/**
 * The implementation of an algorithm allowing to transfer content in a compressed form to the client.
 */
public interface CompressionAlgorithm {

    /**
     * The name of the algorithm as specified by the client in the "Accept-Encoding" HTTP header.
     */
    public val name: AlgorithmName

    /**
     * The file name suffix used for precompressed variants of static files (e.g. "gz" for gzip).
     */
    public val fileExtension: String

    /**
     * The priority of the algorithm. The algorithm with the highest priority will be selected if multiple
     * algorithms can be applied to a response.
     */
    public val priority: Priority

    /**
     * Returns a content instance allowing the server to stream the compressed content.
     *
     * @param content The content of the response to be compressed
     * @param level The compression level to be applied
     * @return A result representing the compressed content
     */
    public fun compress(content: ResponseContent, level: CompressionLevel): ResponseContent

    /**
     * Returns a stream that wraps the request content and decompresses on read.
     *
     * @param content The compressed request content stream
     * @return A stream that provides decompressed content
     */
    public fun decompress(content: InputStream): InputStream

}
