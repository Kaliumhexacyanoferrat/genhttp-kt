package org.genhttp.testing.acceptance.engine.body

import org.genhttp.engine.shared.io.ConnectionReader
import org.genhttp.engine.shared.types.body.LengthLimitedStream

import org.junit.jupiter.api.Test

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.Channels

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LengthLimitedStreamTests {

    @Test
    fun testReadLimitsToContentLength() {
        val reader = ConnectionReader(Channels.newChannel(ByteArrayInputStream("Hello, World!".toByteArray(Charsets.US_ASCII))))

        val stream = LengthLimitedStream(reader, contentLength = 5)

        val buffer = ByteArray(10)
        val read = stream.read(buffer)

        assertEquals(5, read)
        assertEquals("Hello", String(buffer, 0, read, Charsets.US_ASCII))
        assertEquals(5L, stream.position)

        // the end of the stream is signalled by -1 on the JVM
        assertEquals(-1, stream.read(buffer))

        stream.drain() // already exhausted, should just no-op
    }

    @Test
    fun testBasics() {
        val reader = ConnectionReader(Channels.newChannel(ByteArrayInputStream(ByteArray(0))))

        val stream = LengthLimitedStream(reader, contentLength = 5)

        // there is no read/write/seek capability flag on the JVM, the stream is an input stream that cannot be marked
        val instance: Any = stream

        assertTrue(instance is InputStream)
        // seeking (mark/reset) is not supported
        assertFailsWith<java.io.IOException> { stream.reset() }
        assertFalse(stream.markSupported())

        // the length is not exposed, only the number of bytes read so far
        assertEquals(0L, stream.position)

        // flushing, seeking, resizing and writing are not part of an InputStream
    }

}
