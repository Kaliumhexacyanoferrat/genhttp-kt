package org.genhttp.testing.acceptance.utilities

import org.genhttp.engine.shared.io.ConnectionReader

import java.nio.ByteBuffer
import java.nio.channels.ReadableByteChannel
import java.util.ArrayDeque

/**
 * An in-memory connection for tests of the engine internals (the counterpart of `System.IO.Pipelines.Pipe` used by
 * the upstream tests).
 *
 * Data passed to [write] is received by the [reader] as a separate segment, [complete] ends the input. Reads of the
 * reader block until data has been written or the pipe has been completed, so tests that write data while a read is
 * pending run the read on another thread (e.g. `async(Dispatchers.IO) { ... }`).
 */
class TestPipe {

    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN") // wait/notify are needed to block the reader
    private val lock = Object()

    private val segments = ArrayDeque<ByteBuffer>()

    private var completed = false

    private val channel = object : ReadableByteChannel {

        override fun read(dst: ByteBuffer): Int {
            synchronized(lock) {
                while (segments.isEmpty() && !completed) {
                    lock.wait()
                }

                val segment = segments.peekFirst() ?: return -1

                val count = minOf(segment.remaining(), dst.remaining())

                dst.put(dst.position(), segment, segment.position(), count)
                dst.position(dst.position() + count)

                segment.position(segment.position() + count)

                if (!segment.hasRemaining()) {
                    segments.removeFirst()
                }

                return count
            }
        }

        override fun isOpen(): Boolean = true

        override fun close() {
            // nothing to release
        }

    }

    /**
     * The reader of the connection, which receives the written data.
     */
    val reader: ConnectionReader = ConnectionReader(channel)

    /**
     * Passes the given data to the reader.
     */
    fun write(data: ByteArray, offset: Int = 0, length: Int = data.size - offset) {
        synchronized(lock) {
            check(!completed) { "The pipe has been completed already" }

            segments.addLast(ByteBuffer.wrap(data.copyOfRange(offset, offset + length)))
            lock.notifyAll()
        }
    }

    /**
     * Passes the given (ASCII) text to the reader.
     */
    fun write(text: String) {
        write(text.toByteArray(Charsets.US_ASCII))
    }

    /**
     * Ends the input of the reader.
     */
    fun complete() {
        synchronized(lock) {
            completed = true
            lock.notifyAll()
        }
    }

    /**
     * Reads everything the reader has not consumed until the pipe has been completed and returns it as (ASCII)
     * text (`RestAsync` upstream).
     */
    fun rest(): String {
        val result = StringBuilder()
        val buffer = ByteArray(4096)

        while (true) {
            val read = reader.read(buffer, 0, buffer.size)

            if (read < 0) {
                return result.toString()
            }

            result.append(String(buffer, 0, read, Charsets.US_ASCII))
        }
    }

}
