package org.genhttp.testing.acceptance.modules.io

import org.genhttp.modules.io.streaming.WritingStream

import org.junit.jupiter.api.Test

import java.io.ByteArrayOutputStream
import java.io.Flushable
import java.nio.channels.Channels

import kotlin.test.assertEquals

class WritingStreamTests {

    @Test
    fun testReadWrite() {
        val target = ByteArrayOutputStream()

        WritingStream(Channels.newChannel(target), Flushable { }).use { stream ->
            stream.write("Hello".toByteArray(Charsets.US_ASCII), 0, 5)
            stream.write('!'.code)
            stream.flush()

            // the stream is write-only on the JVM, so the written content is read from the target
            assertEquals(6, target.size())

            assertEquals("Hello!", String(target.toByteArray(), Charsets.US_ASCII))
        }
    }

    @Test
    fun testDelegatesToBaseStream() {
        val target = ByteArrayOutputStream()

        var flushed = 0

        WritingStream(Channels.newChannel(target), Flushable { flushed++ }).use { stream ->
            // CanRead/CanSeek, Seek and SetLength do not exist on a JVM OutputStream,
            // so it is verified that writes and flushes are passed on to the underlying channel and flusher
            stream.write("AB".toByteArray(Charsets.US_ASCII))
            assertEquals(2, target.size())

            stream.flush()
            assertEquals(1, flushed)
        }
    }

}
