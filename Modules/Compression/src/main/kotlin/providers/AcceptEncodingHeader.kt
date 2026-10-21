package org.genhttp.modules.compression.providers

import org.genhttp.api.content.io.AlgorithmName
import org.genhttp.api.protocol.ByteString

import java.nio.ByteBuffer

/**
 * Parses the value of the "Accept-Encoding" header sent by the client.
 */
public object AcceptEncodingHeader {

    private const val COMMA: Byte = 0x2C

    private const val SEMICOLON: Byte = 0x3B

    private const val SPACE: Byte = 0x20

    private const val TAB: Byte = 0x09

    /**
     * Determines the names of all algorithms listed in the given header value.
     *
     * @param acceptHeader The value of the "Accept-Encoding" header
     * @return The names of the algorithms listed in the header
     */
    public fun parseSupported(acceptHeader: ByteString): Set<AlgorithmName> {
        val result = HashSet<AlgorithmName>()

        val buffer = acceptHeader.bytes

        var start = 0

        while (start < buffer.remaining()) {
            val end = tokenEnd(buffer, start)

            val (from, to) = nameRange(buffer, start, end)

            if (to > from) {
                val part = ByteArray(to - from)

                buffer.get(buffer.position() + from, part)

                result.add(AlgorithmName(part))
            }

            start = end + 1
        }

        return result
    }

    /**
     * Checks whether the given algorithm is listed in the given header value.
     *
     * @param acceptHeader The value of the "Accept-Encoding" header
     * @param algorithm The name of the algorithm to be checked
     * @return true, if the algorithm is listed in the header
     */
    public fun isSupported(acceptHeader: ByteString, algorithm: AlgorithmName): Boolean {
        val buffer = acceptHeader.bytes

        var start = 0

        while (start < buffer.remaining()) {
            val end = tokenEnd(buffer, start)

            val (from, to) = nameRange(buffer, start, end)

            if (algorithm.contentEquals(buffer.slice(buffer.position() + from, to - from))) {
                return true
            }

            start = end + 1
        }

        return false
    }

    /**
     * Finds the end (exclusive) of the token starting at the given index.
     */
    private fun tokenEnd(buffer: ByteBuffer, start: Int): Int {
        val position = buffer.position()

        for (i in start until buffer.remaining()) {
            if (buffer.get(position + i) == COMMA) {
                return i
            }
        }

        return buffer.remaining()
    }

    /**
     * Finds the range (start inclusive, end exclusive) of the trimmed name within the token, which ends
     * before the first parameter (such as the quality value).
     */
    private fun nameRange(buffer: ByteBuffer, start: Int, end: Int): Pair<Int, Int> {
        val position = buffer.position()

        var nameEnd = end

        for (i in start until end) {
            if (buffer.get(position + i) == SEMICOLON) {
                nameEnd = i
                break
            }
        }

        var from = start
        var to = nameEnd - 1

        while (from <= to && isAsciiWhiteSpace(buffer.get(position + from))) {
            from++
        }

        while (to >= from && isAsciiWhiteSpace(buffer.get(position + to))) {
            to--
        }

        return from to (to + 1)
    }

    private fun isAsciiWhiteSpace(b: Byte): Boolean = b == SPACE || b == TAB

}
