package org.genhttp.modules.io.streaming

import java.io.Flushable
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel

/**
 * An output stream that writes the data to a channel and flushes the underlying transport on demand.
 *
 * If the transport has been closed by the peer, [flush] will fail with a
 * [org.genhttp.api.infrastructure.ConnectionClosedException] (as thrown by the flusher), so a writer that loops
 * until a write fails (e.g. a server-sent event source) stops instead of writing for nobody.
 *
 * @param writer The channel to write the data to
 * @param flusher The component to be flushed if [flush] is called
 */
public class WritingStream(private val writer: WritableByteChannel, private val flusher: Flushable) : OutputStream() {

    private val single = ByteBuffer.allocate(1)

    override fun write(b: Int) {
        single.clear()
        single.put(0, b.toByte())

        writer.writeFully(single)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        writer.writeFully(ByteBuffer.wrap(b, off, len))
    }

    override fun flush() {
        flusher.flush()
    }

}
