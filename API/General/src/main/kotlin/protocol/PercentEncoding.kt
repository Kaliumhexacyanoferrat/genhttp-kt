package org.genhttp.api.protocol

import java.nio.ByteBuffer

/**
 * Shared low level helpers to decode percent-encoded ("URL encoded") bytes.
 */
public object PercentEncoding {

    /**
     * Determines whether the given byte is a valid hexadecimal digit (0-9, a-f, A-F).
     */
    public fun isHexDigit(value: Byte): Boolean {
        val v = value.toInt() and 0xFF
        return (v - '0'.code) in 0..9 || ((v or 0x20) - 'a'.code) in 0..5
    }

    /**
     * Converts a hexadecimal digit byte into its numeric value.
     */
    public fun hexValue(value: Byte): Int {
        val v = value.toInt() and 0xFF
        return if (v <= '9'.code) v - '0'.code else (v or 0x20) - 'a'.code + 10
    }

    /**
     * Attempts to decode the two hex digits following a "%" escape sequence.
     *
     * @return The decoded byte or null, if the given bytes are no valid hex digits
     */
    public fun tryDecode(high: Byte, low: Byte): Byte? {
        if (!isHexDigit(high) || !isHexDigit(low)) {
            return null
        }

        return ((hexValue(high) shl 4) or hexValue(low)).toByte()
    }

    /**
     * Decodes a percent-encoded ("URL encoded") sequence of bytes, only allocating a new buffer if
     * the sequence actually contains encoded characters.
     *
     * @param source The encoded bytes to decode (from position to limit)
     * @param decodePlus Whether a "+" character should be decoded into a space (as used by form encoded content)
     * @return The decoded bytes or source, if there was nothing to decode
     */
    public fun decode(source: ByteBuffer, decodePlus: Boolean = false): ByteBuffer {
        val start = source.position()
        val length = source.remaining()

        var found = false

        for (i in 0 until length) {
            val current = source.get(start + i)

            if (current == PERCENT || (decodePlus && current == PLUS)) {
                found = true
                break
            }
        }

        if (!found) {
            return source
        }

        val target = ByteBuffer.allocate(length)
        val written = decode(source, target, decodePlus)

        return target.limit(written).asReadOnlyBuffer()
    }

    /**
     * Decodes a percent-encoded ("URL encoded") sequence of bytes into the given buffer.
     *
     * The decoded bytes are written beginning at the position of the target buffer. Neither the position of the
     * source nor the position of the target are modified.
     *
     * @param source The encoded bytes to decode (from position to limit)
     * @param target The buffer the decoded bytes are written to (must be at least as large as source)
     * @param decodePlus Whether a "+" character should be decoded into a space (as used by form encoded content)
     * @return The number of bytes written
     */
    public fun decode(source: ByteBuffer, target: ByteBuffer, decodePlus: Boolean = false): Int {
        val start = source.position()
        val length = source.remaining()
        val offset = target.position()

        var write = 0
        var read = 0

        while (read < length) {
            val current = source.get(start + read)

            if (decodePlus && current == PLUS) {
                target.put(offset + write++, SPACE)
            } else if (current == PERCENT && read + 2 < length) {
                val decoded = tryDecode(source.get(start + read + 1), source.get(start + read + 2))

                if (decoded != null) {
                    target.put(offset + write++, decoded)
                    read += 2
                } else {
                    target.put(offset + write++, current)
                }
            } else {
                target.put(offset + write++, current)
            }

            read++
        }

        return write
    }

    private const val PERCENT = '%'.code.toByte()

    private const val PLUS = '+'.code.toByte()

    private const val SPACE = ' '.code.toByte()

}
