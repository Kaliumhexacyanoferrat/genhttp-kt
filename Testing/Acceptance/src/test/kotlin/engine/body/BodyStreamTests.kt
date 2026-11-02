package org.genhttp.testing.acceptance.engine.body

import org.genhttp.engine.shared.types.body.BodyStream
import org.genhttp.engine.shared.types.body.ChunkedFraming
import org.genhttp.engine.shared.types.body.ContentLengthFraming
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.TestPipe

import org.junit.jupiter.api.Test

import java.io.Flushable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

import kotlin.reflect.KMutableProperty
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BodyStreamTests {

    @Test
    fun testBasics() {
        val framing = ContentLengthFraming()
        framing.apply(TestPipe().reader, 5)

        val stream = BodyStream(framing)

        // there are no capability flags on the JVM: the stream is an input stream that cannot be marked (seeked)
        val instance: Any = stream

        assertTrue(instance is InputStream)
        assertFalse(OutputStream::class.java.isInstance(instance))
        assertFalse(stream.markSupported())

        assertEquals(5L, stream.length)
        assertEquals(0L, stream.position)

        // flushing and writing are not part of an input stream
        assertFalse(instance is Flushable)

        // seeking (mark/reset) is not supported
        assertFailsWith<IOException> { stream.reset() }

        // the length and the position cannot be set
        assertFalse(BodyStream::length is KMutableProperty<*>)
        assertFalse(BodyStream::position is KMutableProperty<*>)
    }

    @Test
    fun testChunkedLengthIsUnknown() {
        val framing = ChunkedFraming()
        framing.apply(TestPipe().reader)

        val stream = BodyStream(framing)

        assertFailsWith<UnsupportedOperationException> { stream.length }
    }

    @Test
    fun testReadOverloads() = test {
        val pipe = TestPipe()

        pipe.write("HelloWorld!")
        pipe.complete()

        val framing = ContentLengthFraming()
        framing.apply(pipe.reader, 11)

        val stream = BodyStream(framing)

        val buffer = ByteArray(5)

        assertEquals(5, stream.read(buffer, 0, 5))
        assertEquals("Hello", String(buffer, Charsets.US_ASCII))

        assertEquals(5, stream.read(buffer, 0, 5))
        assertEquals("World", String(buffer, Charsets.US_ASCII))

        assertEquals(1, stream.read(buffer))
        assertEquals(11L, stream.position)

        // the end of the stream is signalled by -1 on the JVM (0 upstream)
        assertEquals(-1, stream.read(buffer))
    }

}
