package org.genhttp.glyph11

import java.nio.ByteBuffer

/**
 * A shared, immutable, zero-length buffer used wherever upstream assigns an empty memory.
 */
internal val EmptyBuffer: ByteBuffer = ByteBuffer.allocate(0).asReadOnlyBuffer()

/**
 * Internal view on the content (`position until limit`) of a [ByteBuffer] with checked, unsigned,
 * absolute byte access. Never touches the position or limit of the wrapped buffer.
 *
 * Heap buffers are read through their backing array, all other buffers (direct, read-only) through
 * absolute `get` calls. All indexes are relative to the position the buffer had on creation.
 */
internal class ByteSpan(private val buffer: ByteBuffer) {

    private val start: Int = buffer.position()

    private val array: ByteArray? = if (buffer.hasArray()) buffer.array() else null

    private val arrayBase: Int = if (array != null) buffer.arrayOffset() + start else 0

    /** The number of bytes in this span. */
    val length: Int = buffer.remaining()

    /** Returns the byte at [index] as an unsigned value (0..255); throws if out of range. */
    fun at(index: Int): Int {
        if (index < 0 || index >= length) {
            throw IndexOutOfBoundsException("Index $index out of bounds for length $length")
        }

        val a = array

        return if (a != null) {
            a[arrayBase + index].toInt() and 0xFF
        } else {
            buffer.get(start + index).toInt() and 0xFF
        }
    }

    /** Index of the first occurrence of [needle] within `[from, to)`, or -1. */
    fun indexOf(needle: ByteArray, from: Int = 0, to: Int = length): Int {
        val size = needle.size
        val last = to - size

        var i = from

        while (i <= last) {
            var j = 0

            while (j < size && at(i + j) == (needle[j].toInt() and 0xFF)) {
                j++
            }

            if (j == size) return i

            i++
        }

        return -1
    }

    /** Index of the first occurrence of the byte [value] within `[from, to)`, or -1. */
    fun indexOfByte(value: Int, from: Int, to: Int): Int {
        var i = from

        while (i < to) {
            if (at(i) == value) return i
            i++
        }

        return -1
    }

    /** True if all bytes within `[from, to)` are flagged in [table]. */
    fun allIn(table: BooleanArray, from: Int, to: Int): Boolean {
        var i = from

        while (i < to) {
            if (!table[at(i)]) return false
            i++
        }

        return true
    }

    /** True if both ranges have the same length and content. */
    fun rangeEquals(aStart: Int, aLength: Int, bStart: Int, bLength: Int): Boolean {
        if (aLength != bLength) return false

        for (i in 0 until aLength) {
            if (at(aStart + i) != at(bStart + i)) return false
        }

        return true
    }

    /** ASCII case-insensitive comparison of `[from, from + len)` with [other] (same semantics as upstream). */
    fun equalsIgnoreCase(from: Int, len: Int, other: ByteArray): Boolean {
        if (len != other.size) return false

        for (i in 0 until len) {
            if ((at(from + i) or 0x20) != ((other[i].toInt() and 0xFF) or 0x20)) return false
        }

        return true
    }

    /** Creates a read-only buffer covering exactly `[offset, offset + len)` of this span. */
    fun slice(offset: Int, len: Int): ByteBuffer = buffer.slice(start + offset, len).asReadOnlyBuffer()

}
