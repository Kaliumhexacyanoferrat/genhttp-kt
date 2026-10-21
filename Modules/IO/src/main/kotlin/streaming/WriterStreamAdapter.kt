package org.genhttp.modules.io.streaming

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel

/**
 * Exposes a [WritableByteChannel] as an [OutputStream].
 *
 * @param writer The channel to write the data to
 */
public class WriterStreamAdapter(private val writer: WritableByteChannel) : OutputStream() {

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
        // data is handed over to the writer immediately
    }

}
