package org.genhttp.engine.shared.types

import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.PathSegment
import org.genhttp.api.protocol.PercentEncoding
import org.genhttp.api.protocol.RequestTarget

import java.nio.ByteBuffer

/**
 * Routing state of a request path (`RequestTarget` in GenHTTP).
 */
public class HttpRequestTarget : RequestTarget {

    private var path: ByteBuffer = EMPTY

    private var offset = 0

    private var segmentStart = 0

    override var current: PathSegment? = null
        private set

    override val isLast: Boolean
        get() {
            if (current == null) {
                return false
            }

            val length = path.remaining()
            var i = offset

            while (i < length && at(i) == SLASH) {
                i++
            }

            return i >= length
        }

    override val hasTrailingSlash: Boolean
        get() {
            val length = path.remaining()
            return length > 0 && at(length - 1) == SLASH
        }

    /**
     * Initializes the routing state for the given path.
     */
    public fun apply(path: ByteString) {
        apply(path.bytes)
    }

    /**
     * Initializes the routing state for the given path as read from the request (instead of copying the view of the
     * byte string created from it). The buffer must not be moved while the state is in use.
     */
    internal fun apply(path: ByteBuffer) {
        this.path = path
        offset = 0
        segmentStart = 0
        current = null

        moveNext()
    }

    override fun next(offset: Int): PathSegment? {
        if (current == null) {
            return null
        }

        if (offset == 0) {
            return current
        }

        val length = path.remaining()
        var i = this.offset

        for (skip in 1..offset) {
            while (i < length && at(i) == SLASH) {
                i++
            }

            if (i >= length) {
                return null
            }

            val start = i
            val idx = indexOfSlash(i)

            if (skip == offset) {
                return PathSegment(slice(start, if (idx < 0) length - start else idx - start))
            }

            i = if (idx < 0) length else idx
        }

        return null
    }

    override fun advance(segments: Int) {
        var remaining = segments

        while (remaining-- > 0 && current != null) {
            moveNext()
        }
    }

    private fun moveNext() {
        val length = path.remaining()

        if (length < 2) {
            current = null
            return
        }

        while (offset < length && at(offset) == SLASH) {
            offset++
        }

        if (offset >= length) {
            current = null
            return
        }

        val start = offset
        segmentStart = start

        val idx = indexOfSlash(offset)

        if (idx < 0) {
            offset = length
            current = PathSegment(slice(start, length - start))
        } else {
            offset = idx
            current = PathSegment(slice(start, idx - start))
        }
    }

    override fun copyAndAppend(suffix: ByteBuffer): RequestTarget {
        val originalLength = path.remaining()
        val combinedArray = ByteArray(originalLength + suffix.remaining())

        path.get(path.position(), combinedArray, 0, originalLength)
        suffix.get(suffix.position(), combinedArray, originalLength, suffix.remaining())

        val copy = HttpRequestTarget()

        copy.path = ByteBuffer.wrap(combinedArray).asReadOnlyBuffer()
        copy.offset = offset
        copy.segmentStart = segmentStart

        if (current != null) {
            val relative = copy.indexOfSlash(segmentStart)

            if (relative < 0) {
                copy.offset = combinedArray.size
                copy.current = PathSegment(copy.slice(segmentStart, combinedArray.size - segmentStart))
            } else {
                copy.offset = relative
                copy.current = PathSegment(copy.slice(segmentStart, relative - segmentStart))
            }
        }

        return copy
    }

    override fun asString(decode: Boolean, remainingOnly: Boolean): String {
        val slice = if (remainingOnly) {
            if (current == null) {
                return ""
            }

            slice(segmentStart, path.remaining() - segmentStart)
        } else {
            path
        }

        val stringPath = ByteString(slice).toString()

        if (decode && stringPath.contains('%')) {
            return unescapeDataString(stringPath)
        }

        return stringPath
    }

    private fun at(index: Int): Byte = path.get(path.position() + index)

    private fun indexOfSlash(from: Int): Int {
        val length = path.remaining()

        for (i in from until length) {
            if (at(i) == SLASH) {
                return i
            }
        }

        return -1
    }

    private fun slice(start: Int, length: Int): ByteBuffer = path.slice(path.position() + start, length)

    private companion object {

        const val SLASH = '/'.code.toByte()

        val EMPTY: ByteBuffer = ByteBuffer.allocate(0).asReadOnlyBuffer()

        /**
         * Decodes valid percent-encoded sequences only and keeps everything else as-is
         * (like `Uri.UnescapeDataString` in .NET).
         */
        fun unescapeDataString(value: String): String {
            val decoded = PercentEncoding.decode(ByteBuffer.wrap(value.toByteArray(Charsets.UTF_8)))
            val bytes = ByteArray(decoded.remaining())
            decoded.get(decoded.position(), bytes)
            return String(bytes, Charsets.UTF_8)
        }

    }

}
