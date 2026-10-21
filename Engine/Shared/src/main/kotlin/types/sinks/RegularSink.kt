package org.genhttp.engine.shared.types.sinks

import org.genhttp.api.protocol.ResponseSink
import org.genhttp.engine.shared.types.ClientContext

import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel

/**
 * Sink for content of known length, writing directly to the connection.
 */
internal class RegularSink(private val context: ClientContext) : ResponseSink {

    override val writer: WritableByteChannel
        get() = context.writer

    override val stream: OutputStream
        get() = context.writer.stream

}
