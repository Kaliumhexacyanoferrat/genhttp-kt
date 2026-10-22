package org.genhttp.modules.files.multi

import org.genhttp.api.content.io.CompressionAlgorithm

import java.nio.ByteBuffer

/**
 * A compression algorithm supported for pre-compressed files, together with the extension that
 * is appended to the path of a requested file to find its compressed sibling.
 *
 * @param algorithm The algorithm used to compress the file
 * @param extension The extension to be appended to the path (including the separator)
 */
public class SupportedCompression(
    public val algorithm: CompressionAlgorithm,
    extension: ByteBuffer
) {

    private val storedExtension: ByteBuffer = extension.asReadOnlyBuffer()

    /**
     * The extension to be appended to the path (including the separator).
     */
    public val extension: ByteBuffer
        get() = storedExtension.duplicate()

}
