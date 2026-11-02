package org.genhttp.testing.acceptance.engine.body

import org.genhttp.api.content.ProviderException
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.engine.shared.types.body.ChunkedFraming
import org.genhttp.testing.acceptance.engine.body.ContentLengthFramingTests.Companion.ascii
import org.genhttp.testing.acceptance.engine.body.ContentLengthFramingTests.Companion.bytes
import org.genhttp.testing.acceptance.test
import org.genhttp.testing.acceptance.utilities.TestPipe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

import org.junit.jupiter.api.Test

import java.io.ByteArrayOutputStream
import java.util.Random

import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChunkedFramingTests {

    @Test
    fun testReadWithSmallBuffer() = test {
        val (pipe, framing) = create(Body + "NEXT")

        val result = StringBuilder()
        val buffer = ByteArray(2)

        var read: Int

        while (framing.read(buffer, 0, buffer.size).also { read = it } > 0) {
            result.append(String(buffer, 0, read, Charsets.US_ASCII))
        }

        assertEquals("Hello World", result.toString())
        assertEquals(11L, framing.position)
        assertNull(framing.length)

        assertEquals(0, framing.read(buffer, 0, buffer.size))

        assertTrue(framing.skip())

        assertEquals("NEXT", pipe.rest())
    }

    @Test
    fun testChunkSpanningSegments() = test {
        val payload = ByteArray(20_000)
        Random(42).nextBytes(payload)

        val pipe = TestPipe()

        pipe.write(hex(payload.size) + "\r\n")
        pipe.write(payload, 0, 5000)
        pipe.write(payload, 5000)
        pipe.write("\r\n0\r\n\r\nNEXT")
        pipe.complete()

        val framing = ChunkedFraming()
        framing.apply(pipe.reader)

        val result = ByteArrayOutputStream()
        val buffer = ByteArray(3000) // does not divide the segment sizes, so reads cross segment boundaries

        var read: Int

        while (framing.read(buffer, 0, buffer.size).also { read = it } > 0) {
            result.write(buffer, 0, read)
        }

        assertContentEquals(payload, result.toByteArray())

        assertTrue(framing.skip())

        assertEquals("NEXT", pipe.rest())
    }

    @Test
    fun testReadWholeChunks() = test {
        val payload = ByteArray(5000)
        Random(13).nextBytes(payload)

        val pipe = TestPipe()

        pipe.write("5\r\nHello\r\n")
        pipe.write(hex(payload.size) + "\r\n")
        pipe.write(payload, 0, 1000)
        pipe.write(payload, 1000)
        pipe.write("\r\n0\r\n\r\nNEXT")
        pipe.complete()

        val framing = ChunkedFraming()
        framing.apply(pipe.reader)

        val buffer = ByteArray(8192)

        assertEquals(5, framing.read(buffer, 0, buffer.size))
        assertEquals("Hello", String(buffer, 0, 5, Charsets.US_ASCII))

        assertEquals(payload.size, framing.read(buffer, 0, buffer.size))
        assertContentEquals(payload, buffer.copyOfRange(0, payload.size))

        assertEquals(5L + payload.size, framing.position)

        assertEquals(0, framing.read(buffer, 0, buffer.size))

        assertTrue(framing.skip())

        assertEquals("NEXT", pipe.rest())
    }

    @Test
    fun testReadToEndOfChunkSpanningSegments() = test {
        val payload = ByteArray(20_000)
        Random(7).nextBytes(payload)

        val pipe = TestPipe()

        pipe.write(hex(payload.size) + "\r\n")
        pipe.write(payload)
        pipe.write("\r\n0\r\n\r\n")
        pipe.complete()

        val framing = ChunkedFraming()
        framing.apply(pipe.reader)

        framing.read(ByteArray(10), 0, 10)

        val memory = framing.readToEnd()

        assertContentEquals(payload.copyOfRange(10, payload.size), bytes(memory))
        assertEquals(payload.size.toLong(), framing.position)
    }

    @Test
    fun testReadIntoEmptyBuffer() = test {
        val (_, framing) = create(Body)

        assertEquals(0, framing.read(ByteArray(0), 0, 0))
    }

    @Test
    fun testReadToEnd() = test {
        val (pipe, framing) = create(Body + "NEXT")

        val memory = framing.readToEnd()

        assertEquals("Hello World", ascii(memory))
        assertEquals(11L, framing.position)

        assertTrue(framing.skip())

        assertEquals("NEXT", pipe.rest())
    }

    @Test
    fun testReadToEndAfterPartialRead() = test {
        val (_, framing) = create(Body)

        framing.read(ByteArray(2), 0, 2)

        val memory = framing.readToEnd()

        assertEquals("llo World", ascii(memory))
    }

    @Test
    fun testReadAsDataArrives() = test {
        val pipe = TestPipe()

        val framing = ChunkedFraming()
        framing.apply(pipe.reader)

        val pending = async(Dispatchers.IO) { framing.readToEnd() }

        for (part in listOf("5\r\nHel", "lo\r\n6\r\n World\r", "\n0\r\n", "\r\n")) {
            pipe.write(part)
        }

        val memory = pending.await()

        assertEquals("Hello World", ascii(memory))
    }

    @Test
    fun testTruncatedBodyFails() = test {
        val (_, framing) = create("5\r\nHel")

        val e = assertFailsWith<ProviderException> { framing.read(ByteArray(16), 0, 16) }

        assertEquals(ResponseStatus.BadRequest, e.status)
    }

    @Test
    fun testMissingBodyFails() = test {
        val (_, framing) = create("")

        assertFailsWith<ProviderException> { framing.read(ByteArray(16), 0, 16) }
    }

    @Test
    fun testMalformedBodyFails() = test {
        val (_, framing) = create("X\r\nHello\r\n0\r\n\r\n")

        val e = assertFailsWith<ProviderException> { framing.readToEnd() }

        assertEquals(ResponseStatus.BadRequest, e.status)
    }

    @Test
    fun testSkipWithinChunk() = test {
        val (pipe, framing) = create(Body + "NEXT")

        framing.read(ByteArray(2), 0, 2)

        assertTrue(framing.skip())

        assertEquals("NEXT", pipe.rest())
    }

    @Test
    fun testSkipUnread() = test {
        val (pipe, framing) = create(Body + "NEXT")

        assertTrue(framing.skip())

        assertEquals("NEXT", pipe.rest())
    }

    @Test
    fun testSkipAsDataArrives() = test {
        val pipe = TestPipe()

        val framing = ChunkedFraming()
        framing.apply(pipe.reader)

        pipe.write("5\r\nHello\r\n6\r\n Wo")

        val pending = async(Dispatchers.IO) { framing.skip() }

        pipe.write("rld\r\n0\r\n\r\nNEXT")
        pipe.complete()

        assertTrue(pending.await())

        assertEquals("NEXT", pipe.rest())
    }

    @Test
    fun testSkipTruncatedBody() = test {
        val (_, framing) = create("5\r\nHel")

        assertFalse(framing.skip())
    }

    @Test
    fun testSkipMalformedBody() = test {
        val (_, framing) = create("X\r\n")

        assertFalse(framing.skip())
    }

    @Test
    fun testReuseAfterReset() = test {
        val (_, framing) = create(Body)

        framing.read(ByteArray(2), 0, 2)

        framing.reset()

        assertEquals(0L, framing.position)

        val pipe = TestPipe()

        pipe.write(Body)
        pipe.complete()

        framing.apply(pipe.reader)

        val memory = framing.readToEnd()

        assertEquals("Hello World", ascii(memory))
    }

    private fun create(content: String): Pair<TestPipe, ChunkedFraming> {
        val pipe = TestPipe()

        pipe.write(content)
        pipe.complete()

        val framing = ChunkedFraming()
        framing.apply(pipe.reader)

        return Pair(pipe, framing)
    }

    /**
     * Formats the given length as an upper-case hexadecimal number (`{length:X}` upstream).
     */
    private fun hex(length: Int): String = length.toString(16).uppercase()

    private companion object {

        const val Body = "5\r\nHello\r\n6\r\n World\r\n0\r\n\r\n"

    }

}
