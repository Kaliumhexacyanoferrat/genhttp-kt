package org.genhttp.engine.shared.io

import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel

/**
 * Provides the buffers a [ConnectionWriter] collects its data in and sends them to the client.
 *
 * By default, the data is collected in pooled direct buffers that are written to a channel. Engines that are based
 * on an I/O framework with buffers of its own can provide such buffers instead, so that the collected data is handed
 * over without being copied.
 */
public interface WriteTarget {

    /**
     * Whether data can still be sent to the client.
     */
    public val isOpen: Boolean

    /**
     * Returns an empty buffer (position 0, limit and capacity as requested) to collect data in.
     */
    public fun rent(capacity: Int): ByteBuffer

    /**
     * Sends the content (position until limit) of a buffer returned by [rent] and takes the buffer back, also if
     * sending fails. The buffer is not used by the writer anymore.
     */
    public fun send(buffer: ByteBuffer)

    /**
     * Takes back a buffer returned by [rent] without sending it.
     */
    public fun release(buffer: ByteBuffer)

    /**
     * Sends the content (position until limit) of a buffer that is owned by the caller (e.g. data that does not fit
     * into a rented buffer).
     */
    public fun write(data: ByteBuffer)

}

/**
 * Collects data in pooled direct buffers and writes them to a channel (blocking until written).
 */
internal class ChannelWriteTarget(private val channel: WritableByteChannel) : WriteTarget {

    override val isOpen: Boolean
        get() = channel.isOpen

    override fun rent(capacity: Int): ByteBuffer = WriteBuffers.rent(capacity)

    override fun send(buffer: ByteBuffer) {
        try {
            write(buffer)
        } finally {
            WriteBuffers.release(buffer)
        }
    }

    override fun release(buffer: ByteBuffer) {
        WriteBuffers.release(buffer)
    }

    override fun write(data: ByteBuffer) {
        while (data.hasRemaining()) {
            channel.write(data)
        }
    }

}
