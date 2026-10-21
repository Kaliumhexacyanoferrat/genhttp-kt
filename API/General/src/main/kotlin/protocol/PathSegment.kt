package org.genhttp.api.protocol

import org.genhttp.api.MemoryView

import java.nio.ByteBuffer

/**
 * A single percent-encoded path segment.
 */
public class PathSegment : MemoryView {

    /**
     * Creates a path segment on the content of the given buffer (from its position to its limit).
     */
    public constructor(value: ByteBuffer) : super(value)

    /**
     * Creates a path segment on the given array.
     */
    public constructor(value: ByteArray) : super(value)

    /**
     * Creates a path segment from the given (ASCII) string.
     */
    public constructor(value: String) : super(value)

    // region Functionality

    /**
     * Percent-decodes this segment and returns the human-readable string.
     */
    public fun decode(): String {
        val source = bytes
        val start = source.position()
        val length = source.remaining()

        var encoded = false

        for (i in 0 until length) {
            if (source.get(start + i) == '%'.code.toByte()) {
                encoded = true
                break
            }
        }

        if (!encoded) {
            return toString()
        }

        val target = ByteBuffer.allocate(length)
        val written = PercentEncoding.decode(source, target)

        return String(target.array(), 0, written, Charsets.UTF_8)
    }

    // endregion

}
