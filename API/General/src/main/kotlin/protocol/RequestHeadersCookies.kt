@file:JvmName("CookieHeaderExtensions")

package org.genhttp.api.protocol

import java.nio.ByteBuffer

// Allows cookies to be read from a header list (e.g. the "Cookie" request header).

/**
 * Searches the given headers for a cookie with the specified name.
 *
 * @param key The name of the cookie to be looked up
 * @return The value of the cookie, if found
 */
public fun RequestHeaders.getCookie(key: ByteString): ByteString? {
    for (i in 0 until count) {
        val (name, value) = getMemoryEntry(i)

        if (!KnownHeaders.Cookie.contentEquals(name)) {
            continue
        }

        val found = findCookie(value, key)

        if (found != null) {
            return found
        }
    }

    return null
}

/**
 * Searches the given headers for a cookie with the specified name.
 *
 * @param key The name of the cookie to be looked up
 * @return The value of the cookie, if found
 */
public fun RequestHeaders.getCookie(key: String): String? = getCookie(ByteString(key))?.toString()

/**
 * Collects all cookies found in the given headers into a list that can be iterated or queried by name.
 *
 * @return The cookies found in the given headers
 */
public fun RequestHeaders.getCookies(): KeyValueList {
    val cookies = mutableListOf<Pair<ByteBuffer, ByteBuffer>>()

    for (i in 0 until count) {
        val (name, value) = getMemoryEntry(i)

        if (KnownHeaders.Cookie.contentEquals(name)) {
            parseCookies(value, cookies)
        }
    }

    return CookieList(cookies)
}

private fun findCookie(header: ByteBuffer, key: ByteString): ByteString? {
    val keyBytes = key.bytes
    val segments = CookieSegments(header)

    while (segments.moveNext()) {
        val name = slice(header, segments.nameStart, segments.nameEnd)

        if (name.mismatch(keyBytes) == -1) {
            return ByteString(slice(header, segments.valueStart, segments.valueEnd))
        }
    }

    return null
}

private fun parseCookies(header: ByteBuffer, target: MutableList<Pair<ByteBuffer, ByteBuffer>>) {
    val segments = CookieSegments(header)

    while (segments.moveNext()) {
        target.add(slice(header, segments.nameStart, segments.nameEnd) to slice(header, segments.valueStart, segments.valueEnd))
    }
}

private fun slice(buffer: ByteBuffer, start: Int, end: Int): ByteBuffer =
    buffer.slice(buffer.position() + start, end - start).asReadOnlyBuffer()

/**
 * Walks a "Cookie" header value, yielding the name/value ranges of each "name=value" pair
 * it finds (separated by "; "). Ranges are relative to the position of the header buffer.
 */
private class CookieSegments(private val header: ByteBuffer) {

    private val start = header.position()

    private val length = header.remaining()

    private var position = 0

    var nameStart = 0

    var nameEnd = 0

    var valueStart = 0

    var valueEnd = 0

    fun moveNext(): Boolean {
        while (position < length) {
            while (position < length && at(position) == SPACE) {
                position++
            }

            if (position >= length) {
                return false
            }

            val segmentStart = position

            var segmentEnd = length

            for (i in segmentStart until length) {
                if (at(i) == SEMICOLON) {
                    segmentEnd = i
                    break
                }
            }

            var separator = -1

            for (i in segmentStart until segmentEnd) {
                if (at(i) == EQUALS) {
                    separator = i
                    break
                }
            }

            position = segmentEnd + 1

            if (separator > -1) {
                nameStart = segmentStart
                nameEnd = separator
                valueStart = separator + 1
                valueEnd = segmentEnd
                return true
            }
        }

        return false
    }

    private fun at(index: Int): Byte = header.get(start + index)

    private companion object {
        const val SPACE = ' '.code.toByte()
        const val SEMICOLON = ';'.code.toByte()
        const val EQUALS = '='.code.toByte()
    }

}

/**
 * A read-only list of cookies that have been parsed from one or more "Cookie" header values.
 */
private class CookieList(private val entries: List<Pair<ByteBuffer, ByteBuffer>>) : KeyValueList {

    override val count: Int
        get() = entries.size

    override fun getMemoryEntry(index: Int): Pair<ByteBuffer, ByteBuffer> = entries[index]

}
