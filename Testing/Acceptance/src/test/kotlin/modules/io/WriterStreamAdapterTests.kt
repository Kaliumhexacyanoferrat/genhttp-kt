package org.genhttp.testing.acceptance.modules.io

import org.genhttp.modules.io.streaming.WriterStreamAdapter

import org.junit.jupiter.api.Test

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.channels.Channels

import kotlin.test.assertEquals
import kotlin.test.assertFalse

class WriterStreamAdapterTests {

    @Test
    fun testWrite() {
        val writer = ByteArrayOutputStream()

        WriterStreamAdapter(Channels.newChannel(writer)).use { stream ->
            stream.write('H'.code)
            stream.write("i".toByteArray(Charsets.US_ASCII), 0, 1)
            stream.write("!".toByteArray(Charsets.US_ASCII))
            stream.flush()
        }

        assertEquals("Hi!", String(writer.toByteArray(), Charsets.US_ASCII))
    }

    @Test
    fun testBasics() {
        val writer = ByteArrayOutputStream()

        WriterStreamAdapter(Channels.newChannel(writer)).use { stream ->
            // CanRead/CanSeek, Read, Seek, SetLength, Length and Position (all NotSupported in C#) do not exist
            // on a JVM OutputStream, so only the behavior of Flush remains to be checked
            stream.flush()

            assertEquals(0, writer.size())
        }
    }

    @Test
    fun testReadAsyncNotSupported() {
        val writer = ByteArrayOutputStream()

        WriterStreamAdapter(Channels.newChannel(writer)).use { stream ->
            // reading is not supported by construction, the adapter is no InputStream
            assertFalse((stream as Any) is InputStream)
        }
    }

    @Test
    fun testWriteAsyncByteArrayOverload() {
        val writer = ByteArrayOutputStream()

        WriterStreamAdapter(Channels.newChannel(writer)).use { stream ->
            stream.write("Hi".toByteArray(Charsets.US_ASCII), 0, 2)

            assertEquals("Hi", String(writer.toByteArray(), Charsets.US_ASCII))

            // cancellation tokens do not exist on the JVM, writing an empty array is a no-op
            stream.write(ByteArray(0), 0, 0)

            assertEquals("Hi", String(writer.toByteArray(), Charsets.US_ASCII))
        }
    }

    @Test
    fun testFlushAsyncCancelled() {
        val writer = ByteArrayOutputStream()

        WriterStreamAdapter(Channels.newChannel(writer)).use { stream ->
            // cancellation tokens do not exist on the JVM, flushing never writes anything on its own
            stream.flush()

            assertEquals(0, writer.size())
        }
    }

}
