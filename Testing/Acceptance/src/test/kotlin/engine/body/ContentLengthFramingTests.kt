package org.genhttp.testing.acceptance.engine.body

import org.genhttp.api.content.ProviderException
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.engine.shared.types.body.ContentLengthFraming
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.TestPipe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

import org.junit.jupiter.api.Test

import java.nio.ByteBuffer
import java.util.Random

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContentLengthFramingTests {

    @Test
    fun testReadStopsAtLength() = test {
        val (pipe, framing) = create("HelloNEXT", 5)

        val buffer = ByteArray(16)

        val read = framing.read(buffer, 0, buffer.size)

        assertEquals("Hello", String(buffer, 0, read, Charsets.US_ASCII))
        assertEquals(5L, framing.position)
        assertEquals(5L, framing.length)

        assertEquals(0, framing.read(buffer, 0, buffer.size))

        assertTrue(framing.skip())

        assertEquals("NEXT", pipe.rest())
    }

    @Test
    fun testReadIntoEmptyBuffer() = test {
        val (_, framing) = create("Hello", 5)

        assertEquals(0, framing.read(ByteArray(0), 0, 0))
        assertEquals(0L, framing.position)
    }

    @Test
    fun testReadWithSmallBuffer() = test {
        val (pipe, framing) = create("HelloNEXT", 5)

        val result = StringBuilder()
        val buffer = ByteArray(2)

        var read: Int

        while (framing.read(buffer, 0, buffer.size).also { read = it } > 0) {
            result.append(String(buffer, 0, read, Charsets.US_ASCII))
        }

        assertEquals("Hello", result.toString())

        assertTrue(framing.skip())

        assertEquals("NEXT", pipe.rest())
    }

    @Test
    fun testReadTruncatedBodyFails() = test {
        val (_, framing) = create("abc", 5)

        val buffer = ByteArray(16)

        assertEquals(3, framing.read(buffer, 0, buffer.size))

        val e = assertFailsWith<ProviderException> { framing.read(buffer, 0, buffer.size) }

        assertEquals(ResponseStatus.BadRequest, e.status)
    }

    @Test
    fun testReadToEndKeepsSingleSegment() = test {
        val (pipe, framing) = create("HelloNEXT", 5)

        val memory = framing.readToEnd()

        assertEquals("Hello", ascii(memory))
        assertEquals(5L, framing.position)

        assertTrue(framing.skip())

        assertEquals("NEXT", pipe.rest())
    }

    @Test
    fun testReadToEndLinearizesSegments() = test {
        val payload = ByteArray(32 * 1024)
        Random(42).nextBytes(payload)

        val pipe = TestPipe()

        pipe.write(payload, 0, 1024)

        val framing = ContentLengthFraming()
        framing.apply(pipe.reader, payload.size.toLong())

        val pending = async(Dispatchers.IO) { framing.readToEnd() }

        pipe.write(payload, 1024)
        pipe.write("NEXT")
        pipe.complete()

        val memory = pending.await()

        assertContentEquals(payload, bytes(memory))

        assertTrue(framing.skip())

        assertEquals("NEXT", pipe.rest())
    }

    @Test
    fun testReadToEndWithoutLength() = test {
        // nothing has been written and the pipe is still open, so this would hang if it tried to read
        val pipe = TestPipe()

        val framing = ContentLengthFraming()
        framing.apply(pipe.reader, 0)

        val memory = framing.readToEnd()

        assertTrue(memory.remaining() == 0)
        assertTrue(framing.skip())
    }

    @Test
    fun testReadToEndTruncatedBodyFails() = test {
        val (_, framing) = create("abc", 5)

        val e = assertFailsWith<ProviderException> { framing.readToEnd() }

        assertEquals(ResponseStatus.BadRequest, e.status)
    }

    @Test
    fun testSkipAfterPartialRead() = test {
        val (pipe, framing) = create("HelloWorldNEXT", 10)

        val buffer = ByteArray(3)

        framing.read(buffer, 0, buffer.size)

        assertTrue(framing.skip())

        assertEquals("NEXT", pipe.rest())
    }

    @Test
    fun testSkipWaitsForData() = test {
        val pipe = TestPipe()

        pipe.write("Hello")

        val framing = ContentLengthFraming()
        framing.apply(pipe.reader, 10)

        val pending = async(Dispatchers.IO) { framing.skip() }

        pipe.write("WorldNEXT")
        pipe.complete()

        assertTrue(pending.await())

        assertEquals("NEXT", pipe.rest())
    }

    @Test
    fun testSkipTruncatedBody() = test {
        val (_, framing) = create("abc", 5)

        assertFalse(framing.skip())
    }

    private fun create(content: String, length: Long): Pair<TestPipe, ContentLengthFraming> {
        val pipe = TestPipe()

        pipe.write(content)
        pipe.complete()

        val framing = ContentLengthFraming()
        framing.apply(pipe.reader, length)

        return Pair(pipe, framing)
    }

    internal companion object {

        /**
         * Decodes the content of the given buffer as ASCII (`Encoding.ASCII.GetString(memory.Span)` upstream).
         */
        fun ascii(memory: ByteBuffer): String = String(bytes(memory), Charsets.US_ASCII)

        /**
         * Copies the content of the given buffer into an array (`memory.ToArray()` upstream).
         */
        fun bytes(memory: ByteBuffer): ByteArray {
            val result = ByteArray(memory.remaining())
            memory.duplicate().get(result)
            return result
        }

    }

}
