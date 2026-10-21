package org.genhttp.testing.acceptance.modules.io

import org.genhttp.modules.io.ranges.RangedStream

import org.junit.jupiter.api.Test

import java.io.ByteArrayOutputStream

import kotlin.test.assertEquals

class RangedStreamTests {

    @Test
    fun testFullRange() {
        assertEquals("0123456789", getRange(0, 9, 0, 10))
    }

    @Test
    fun testNotAllWritten() {
        assertEquals("23", getRange(0, 9, 2, 2))
    }

    @Test
    fun testRangeExtracted() {
        assertEquals("3456", getRange(3, 6, 0, 10))
    }

    @Test
    fun testNothingToWrite() {
        assertEquals("", getRange(12, 14, 0, 10))
    }

    @Test
    fun testEndOfLargeFile() {
        assertEquals("12345", getRange(10_001, 10_005, 0, 10, 10_000))
    }

    @Test
    fun testSomewhereInLargeFile() {
        assertEquals("0123456789", getRange(0, 10_000, 0, 10, 5000))
    }

    @Test
    fun testOutOfLargeFile() {
        assertEquals("", getRange(0, 10_000, 0, 10, 15_000))
    }

    @Test
    fun testEndClampsToPositionNotStart() {
        assertEquals("01", getRange(2, 5, 0, 10, 4))
    }

    @Test
    fun testMultipleBuffersDoNotExceedRange() {
        val target = ByteArrayOutputStream()

        val bufferSize = 8
        val start = 6L
        val end = 17L // inclusive -> 12 bytes expected

        RangedStream(target, start, end).use { stream ->
            val source = ByteArray(32)

            for (i in source.indices) {
                source[i] = i.toByte()
            }

            var offset = 0

            while (offset < source.size) {
                stream.write(source, offset, bufferSize)

                offset += bufferSize
            }
        }

        val written = target.toByteArray()

        assertEquals((end - start + 1).toInt(), written.size)

        for (i in written.indices) {
            assertEquals((start + i).toByte(), written[i])
        }
    }

    @Test
    fun testBasics() {
        RangedStream(ByteArrayOutputStream(), 0, 10).use { stream ->
            assertEquals(0L, stream.position)
            assertEquals(10L, stream.length)

            // CanWrite/CanRead/CanSeek as well as the NotSupportedException of Read, Seek and SetLength
            // have no equivalent on the JVM: an OutputStream can neither be read, nor seeked, nor resized
        }
    }

    private fun getRange(start: Long, end: Long, offset: Int, count: Int, position: Long = 0): String {
        val target = ByteArrayOutputStream()

        RangedStream(target, start, end).use { stream ->
            stream.position = position

            stream.write("0123456789".toByteArray(Charsets.US_ASCII), offset, count)
        }

        return String(target.toByteArray(), Charsets.US_ASCII)
    }

}
