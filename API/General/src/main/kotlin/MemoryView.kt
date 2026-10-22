package org.genhttp.api

import java.nio.ByteBuffer

/**
 * A typed view onto a chunk of memory read from the request buffer, such as the protocol version,
 * the request path or the content type of the request body.
 *
 * GenHTTP generates these members for every struct marked with `[MemoryView]`. In genhttp-kt,
 * typed views (such as [org.genhttp.api.protocol.ByteString] or [org.genhttp.api.protocol.ContentType])
 * derive from this class instead.
 *
 * Views compare ASCII case-insensitively, have a stable case-insensitive hash and are only equal
 * to views of the same type. The view does not copy the memory it has been created from.
 *
 * GenHTTP declares the views as structs. The JVM has no user-defined value types, so views are objects
 * holding a single read-only buffer; the hash is computed on first use.
 */
public abstract class MemoryView {

    private val value: ByteBuffer

    // computed lazily like String.hashCode(), racy but benign as the result is always the same
    private var hash: Int = 0

    private var hashIsZero: Boolean = false

    // region Initialization

    /**
     * Creates a view on the content of the given buffer (from its position to its limit).
     * The buffer is not copied, so its content must not change afterward.
     *
     * @param value The memory to create a view on
     */
    protected constructor(value: ByteBuffer) {
        val slice = value.slice()

        // views sliced from request data are read-only already
        this.value = if (slice.isReadOnly) slice else slice.asReadOnlyBuffer()
    }

    /**
     * Creates a view on the given array. The array is not copied, so its content must not change afterward.
     *
     * @param value The memory to create a view on
     */
    protected constructor(value: ByteArray) {
        this.value = ByteBuffer.wrap(value).asReadOnlyBuffer()
    }

    /**
     * Creates a view from the given string by converting each character into a single byte.
     *
     * @param value The (ASCII) string to create a view from
     */
    protected constructor(value: String) : this(fromString(value))

    // endregion

    // region Accessors

    /**
     * The memory this view is based on, as a read-only buffer (content from position to limit).
     *
     * A new buffer instance is returned on every access, so callers may freely move its position.
     */
    public val bytes: ByteBuffer
        get() = value.duplicate()

    /**
     * The number of bytes in this view.
     */
    public val length: Int
        get() = value.remaining()

    /**
     * Whether this view does not contain any bytes.
     */
    public val isEmpty: Boolean
        get() = !value.hasRemaining()

    // endregion

    // region Equality

    /**
     * Checks whether this view contains the same (ASCII case-insensitive) content as the given buffer
     * (from its position to its limit).
     *
     * @param other The memory to compare this view with
     * @return true, if both contain the same bytes (ignoring ASCII case)
     */
    public fun contentEquals(other: ByteBuffer): Boolean = equalsIgnoreCase(value, other)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || other.javaClass != javaClass) return false

        other as MemoryView

        // only compare hashes that are known already, computing them would read the content anyway
        if (hash != 0 && other.hash != 0 && hash != other.hash) return false

        return equalsIgnoreCase(value, other.value)
    }

    override fun hashCode(): Int {
        var current = hash

        if (current == 0 && !hashIsZero) {
            current = computeHash(value)

            if (current == 0) {
                hashIsZero = true
            } else {
                hash = current
            }
        }

        return current
    }

    /**
     * Returns the ASCII representation of this view (bytes outside of the ASCII range are rendered as `?`).
     */
    override fun toString(): String {
        val length = value.remaining()
        val chars = CharArray(length)

        for (i in 0 until length) {
            val b = value.get(value.position() + i).toInt()
            chars[i] = if (b < 0) '?' else b.toChar()
        }

        return String(chars)
    }

    // endregion

    private companion object {

        private const val MASK = 0x2020202020202020L

        private const val PRIME = -0x61c8864680b583ebL // 0x9E3779B97F4A7C15

        private const val OFFSET = -0x340d631b7bdddcdbL // 14695981039346656037

        private fun fromString(value: String): ByteArray {
            val bytes = ByteArray(value.length)

            for (i in value.indices) {
                bytes[i] = value[i].code.toByte()
            }

            return bytes
        }

        private fun normalize(value: Int): Int {
            val lower = value or 0x20
            return if (lower in 'a'.code..'z'.code) lower else value
        }

        private fun computeHash(buffer: ByteBuffer): Int {
            val start = buffer.position()
            val length = buffer.remaining()

            var hash = OFFSET xor length.toLong()
            var offset = 0

            while (length - offset >= 8) {
                var chunk = 0L

                for (i in 0 until 8) {
                    chunk = chunk or ((buffer.get(start + offset + i).toLong() and 0xFF) shl (i * 8))
                }

                chunk = chunk or MASK
                hash = (java.lang.Long.rotateLeft(hash, 5) xor chunk) * PRIME
                offset += 8
            }

            if (offset < length) {
                var tail = 0L

                for (i in 0 until length - offset) {
                    tail = tail or (((buffer.get(start + offset + i).toInt() and 0xFF) or 0x20).toLong() shl (i * 8))
                }

                hash = (java.lang.Long.rotateLeft(hash, 5) xor tail) * PRIME
            }

            hash = hash xor (hash ushr 32)

            return hash.toInt()
        }

        private fun equalsIgnoreCase(a: ByteBuffer, b: ByteBuffer): Boolean {
            val length = a.remaining()

            if (length != b.remaining()) {
                return false
            }

            if (a.mismatch(b) == -1) {
                return true
            }

            val aStart = a.position()
            val bStart = b.position()

            for (i in 0 until length) {
                val x = a.get(aStart + i).toInt() and 0xFF
                val y = b.get(bStart + i).toInt() and 0xFF

                if (normalize(x) != normalize(y)) {
                    return false
                }
            }

            return true
        }

    }

}
