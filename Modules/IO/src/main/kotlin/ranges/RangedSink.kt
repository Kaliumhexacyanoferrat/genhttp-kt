package org.genhttp.modules.io.ranges

import org.genhttp.api.protocol.ResponseSink
import org.genhttp.modules.io.streaming.StreamBufferWriter

import java.io.OutputStream
import java.nio.channels.WritableByteChannel

/**
 * A response sink that only forwards a portion of the written data.
 *
 * @param stream The stream the content is written to
 */
public class RangedSink(stream: RangedStream) : ResponseSink {

    private val source: RangedStream = stream

    private var channel: StreamBufferWriter? = null

    override val writer: WritableByteChannel
        get() = channel ?: StreamBufferWriter(source).also { channel = it }

    override val stream: OutputStream
        get() = source

}
