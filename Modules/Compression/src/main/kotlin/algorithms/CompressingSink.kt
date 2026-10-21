package org.genhttp.modules.compression.algorithms

import org.genhttp.api.protocol.ResponseSink
import org.genhttp.modules.io.streaming.StreamBufferWriter

import java.io.OutputStream
import java.nio.channels.WritableByteChannel

/**
 * A response sink that compresses everything written to it using the given compressor and passes
 * the compressed data to the inner sink.
 *
 * Closing the sink finishes the compression exactly once, the inner sink is not closed.
 */
internal class CompressingSink(inner: ResponseSink, compressor: Compressor) : ResponseSink, AutoCloseable {

    private val compressed: OutputStream = compressor.wrap(NonClosingOutputStream(inner.stream))

    private var channel: StreamBufferWriter? = null

    private var closed = false

    override val writer: WritableByteChannel
        get() = channel ?: StreamBufferWriter(compressed).also { channel = it }

    override val stream: OutputStream
        get() = compressed

    override fun close() {
        if (closed) {
            return
        }

        closed = true

        compressed.close()
    }

}
