package org.genhttp.modules.mcp.protocol

import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseSink

import java.nio.ByteBuffer

/**
 * A serialized JSON-RPC message sent as the body of a response.
 */
internal class RpcContent(private val content: ByteArray) : ResponseContent {

    // region Get-/Setters

    override val length: Long?
        get() = content.size.toLong()

    override val type: ContentType?
        get() = ContentType.ApplicationJson

    override val encoding: ByteBuffer?
        get() = null

    // endregion

    // region Functionality

    override suspend fun calculateChecksum(): Long? {
        // FNV-1a (64 bit)
        var hash = -0x340d631b7bdddcdbL

        for (b in content) {
            hash = (hash xor (b.toLong() and 0xFF)) * 0x100000001b3L
        }

        return hash
    }

    override suspend fun write(sink: ResponseSink) {
        val buffer = ByteBuffer.wrap(content)

        while (buffer.hasRemaining()) {
            sink.writer.write(buffer)
        }
    }

    // endregion

}
