package org.genhttp.modules.io.ranges

import java.io.OutputStream

/**
 * A stream that can be configured to just write a specified
 * portion of the incoming data into the underlying stream.
 *
 * @param target The stream to write to
 * @param start The zero based index of the starting position
 * @param end The zero based index of the inclusive end position
 */
public class RangedStream(private val target: OutputStream, start: Long, end: Long) : OutputStream() {

    private val start: Long = start

    private val end: Long = end

    /**
     * The length of the portion written to the target stream.
     */
    public val length: Long
        get() = end - start

    /**
     * The number of bytes that have passed this stream (including those not forwarded to the target).
     */
    public var position: Long = 0

    override fun write(b: Int) {
        write(byteArrayOf(b.toByte()), 0, 1)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        if (position > end || (position + len) <= start) {
            position += len
            return
        }

        val writeStart = maxOf(position, start)
        val writeEnd = minOf(position + len - 1, end)

        val actualOffset = off + (writeStart - position)
        val actualCount = writeEnd - writeStart + 1

        target.write(b, actualOffset.toInt(), actualCount.toInt())

        position += len
    }

    override fun flush() {
        target.flush()
    }

}
