package org.genhttp.api.protocol

import org.genhttp.api.MemoryView

import java.nio.ByteBuffer
import java.nio.CharBuffer

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

    /**
     * Percent-decodes this segment into the given buffer, so that callers can build a path from several segments
     * without creating a string per segment (`Decode(Span<char>)` upstream).
     *
     * @param target The buffer to write to (must provide at least as many chars as the segment has bytes), the chars
     *               are written at its position, which is advanced accordingly
     * @return The number of chars written to the target
     */
    public fun decode(target: CharBuffer): Int {
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
            for (i in 0 until length) {
                target.put((source.get(start + i).toInt() and 0xFF).toChar())
            }

            return length
        }

        val decoded = ByteBuffer.allocate(length)
        val written = PercentEncoding.decode(source, decoded)

        decoded.limit(written)

        val before = target.position()

        Charsets.UTF_8.newDecoder().decode(decoded, target, true)

        return target.position() - before
    }

    // endregion

}
