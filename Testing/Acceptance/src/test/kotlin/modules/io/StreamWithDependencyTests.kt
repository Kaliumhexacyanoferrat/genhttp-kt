package org.genhttp.testing.acceptance.modules.io

import org.genhttp.modules.io.streaming.StreamWithDependency

import org.junit.jupiter.api.Test

import java.io.ByteArrayInputStream

import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StreamWithDependencyTests {

    @Test
    fun testWrapper() {
        var closed = false

        val source = ByteArrayInputStream("Hello World ".toByteArray(Charsets.UTF_8))

        val stream = StreamWithDependency(source, AutoCloseable { closed = true })

        // a JVM input stream cannot be written to or resized, so the wrapper is verified by reading
        // (the C# test seeks, appends and truncates the wrapped MemoryStream)
        assertTrue(stream.markSupported())

        assertEquals(12, stream.available())

        assertEquals(1L, stream.skip(1))

        val buffer = ByteArray(11)

        assertEquals(11, stream.read(buffer, 0, buffer.size))

        assertEquals("ello World ", String(buffer, Charsets.UTF_8))

        stream.reset()

        assertEquals('H'.code, stream.read())

        stream.close()

        assertTrue(closed)
    }

}
