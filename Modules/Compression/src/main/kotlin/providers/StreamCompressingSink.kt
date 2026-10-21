package org.genhttp.modules.compression.providers

import org.genhttp.api.protocol.ResponseSink
import org.genhttp.modules.compression.algorithms.NonClosingOutputStream
import org.genhttp.modules.io.streaming.StreamBufferWriter

import java.io.OutputStream
import java.nio.channels.WritableByteChannel

/**
 * A response sink that passes everything written to it through a compressing stream created by the given generator.
 *
 * Closing the sink closes the compressing stream (finishing the compression), but not the stream of the original sink.
 */
internal class StreamCompressingSink(originalSink: ResponseSink, generator: (OutputStream) -> OutputStream) : ResponseSink, AutoCloseable {

    private var channel: StreamBufferWriter? = null

    private var closed = false

    override val stream: OutputStream = generator(NonClosingOutputStream(originalSink.stream))

    override val writer: WritableByteChannel
        get() = channel ?: StreamBufferWriter(stream).also { channel = it }

    override fun close() {
        if (closed) {
            return
        }

        closed = true

        stream.close()
    }

}
