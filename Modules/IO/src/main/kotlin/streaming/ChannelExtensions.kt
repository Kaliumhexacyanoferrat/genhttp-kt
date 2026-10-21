package org.genhttp.modules.io.streaming

import java.nio.ByteBuffer
import java.nio.channels.WritableByteChannel

/**
 * Writes the remaining content of the buffer to the channel, even if the channel only accepts a part of it per call.
 */
internal fun WritableByteChannel.writeFully(buffer: ByteBuffer) {
    while (buffer.hasRemaining()) {
        write(buffer)
    }
}
