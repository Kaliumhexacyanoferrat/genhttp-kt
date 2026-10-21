package org.genhttp.engine.shared.types.sinks

import org.genhttp.api.protocol.ResponseSink
import org.genhttp.engine.shared.types.ClientContext

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel

/**
 * Sink for content of unknown length, applying chunked transfer encoding.
 */
internal class ChunkedSink(private val context: ClientContext) : ResponseSink {

    private val chunkedWriter = ChunkedWriter(context)

    override val writer: WritableByteChannel
        get() = chunkedWriter

    override val stream: OutputStream = ChunkedStream(chunkedWriter)

    fun finish() = chunkedWriter.finish()

}

/**
 * Stream writing every block of data as a single chunk.
 */
internal class ChunkedStream(private val writer: ChunkedWriter) : OutputStream() {

    override fun write(b: Int) = writer.write(byteArrayOf(b.toByte()), 0, 1)

    override fun write(b: ByteArray, off: Int, len: Int) = writer.write(b, off, len)

    override fun flush() = writer.flush()

    override fun close() {
        // owned by the connection
    }

}
