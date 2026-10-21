package org.genhttp.modules.io.streaming

import org.genhttp.api.infrastructure.BufferSize

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.WritableByteChannel

/**
 * Adapts an [OutputStream] to a [WritableByteChannel], so content that has been written to a response sink
 * can be forwarded to a stream.
 *
 * Closing the writer does not close the underlying stream, which stays owned by the caller.
 *
 * @param stream The stream to write the data to
 * @param bufferSize The size of the buffer used to copy data that is not backed by an accessible array
 */
public class StreamBufferWriter(private val stream: OutputStream, private val bufferSize: Int = BufferSize.Write) : WritableByteChannel {

    private var buffer: ByteArray? = null

    private var open = true

    override fun write(src: ByteBuffer): Int {
        if (!open) {
            throw ClosedChannelException()
        }

        val count = src.remaining()

        if (src.hasArray()) {
            stream.write(src.array(), src.arrayOffset() + src.position(), count)
            src.position(src.limit())
        } else {
            val scratch = buffer ?: ByteArray(bufferSize).also { buffer = it }

            while (src.hasRemaining()) {
                val chunk = minOf(src.remaining(), scratch.size)

                src.get(scratch, 0, chunk)
                stream.write(scratch, 0, chunk)
            }
        }

        return count
    }

    override fun isOpen(): Boolean = open

    override fun close() {
        open = false
    }

}
