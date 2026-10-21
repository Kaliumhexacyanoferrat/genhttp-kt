package org.genhttp.modules.compression.algorithms

import java.io.OutputStream

/**
 * Applies a compression algorithm to the data written to a sink (`ICompressor` in GenHTTP).
 *
 * As the JVM compression libraries work on streams, the compressor creates the stream that compresses
 * everything written to it (flushing it will flush the compressor) and passes the compressed data to the
 * given target.
 */
internal interface Compressor {

    /**
     * Creates a stream that compresses the data written to it.
     *
     * Closing the stream finishes the compression and writes all remaining data to the target.
     *
     * @param target The stream to write the compressed data to
     * @return The stream to write uncompressed data to
     */
    fun wrap(target: OutputStream): OutputStream

}
