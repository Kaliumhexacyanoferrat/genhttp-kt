package org.genhttp.testing.acceptance.engine.body

import org.genhttp.api.content.ProviderException
import org.genhttp.engine.shared.io.ConnectionReader
import org.genhttp.engine.shared.types.body.ChunkedBodyStream

import org.junit.jupiter.api.Test

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.Channels

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChunkedBodyStreamTests {

    @Test
    fun testReadAcrossOverflow() {
        val stream = create("5\r\nHello\r\n0\r\n\r\n")

        val result = ByteArrayOutputStream()
        val buffer = ByteArray(2) // smaller than the chunk, forces the overflow path

        var read: Int

        while (stream.read(buffer).also { read = it } > 0) {
            result.write(buffer, 0, read)
        }

        assertEquals("Hello", String(result.toByteArray(), Charsets.US_ASCII))

        stream.drain() // already completed, should just no-op
    }

    @Test
    fun testBasics() {
        val stream = create("")

        // there is no read/write/seek capability flag on the JVM, the stream is an input stream that cannot be marked
        val instance: Any = stream

        assertTrue(instance is InputStream)
        // seeking (mark/reset) is not supported
        assertFailsWith<java.io.IOException> { stream.reset() }
        assertFalse(stream.markSupported())

        // flushing, seeking, resizing, writing and querying length or position are not part of an InputStream
    }

    @Test
    fun testReadAsyncByteArrayOverload() {
        val stream = create("5\r\nHello\r\n0\r\n\r\n")

        val buffer = ByteArray(5)
        val read = stream.read(buffer, 0, buffer.size)

        assertEquals(5, read)
        assertEquals("Hello", String(buffer, Charsets.US_ASCII))
    }

    @Test
    fun testReadIntoEmptyBufferReturnsZero() {
        val stream = create("5\r\nHello\r\n0\r\n\r\n")

        assertEquals(0, stream.read(ByteArray(0)))
    }

    @Test
    fun testReadOnEmptyCompletedPipeReturnsZero() {
        val stream = create("")

        // the end of the stream is signalled by -1 on the JVM
        assertEquals(-1, stream.read(ByteArray(16)))
    }

    @Test
    fun testTruncatedChunkThrows() {
        val stream = create("5\r\nHel")

        assertFailsWith<ProviderException> { stream.read(ByteArray(16)) }
    }

    @Test
    fun testDrainOnEmptyCompletedPipe() {
        val stream = create("")

        stream.drain()
    }

    @Test
    fun testDrainOnTruncatedChunkReturnsWithoutThrowing() {
        val stream = create("5\r\nHel")

        stream.drain()
    }

    private fun create(chunkedBody: String): ChunkedBodyStream {
        val reader = ConnectionReader(Channels.newChannel(ByteArrayInputStream(chunkedBody.toByteArray(Charsets.US_ASCII))))

        return ChunkedBodyStream(reader)
    }

}
