package org.genhttp.modules.io.ranges

import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseSink

import java.nio.ByteBuffer

/**
 * Response content that only provides a portion of the given source.
 *
 * @param source The content to be sent partially
 * @param start The zero based index of the first byte to be sent
 * @param end The zero based index of the last byte to be sent (inclusive)
 */
public class RangedContent(private val source: ResponseContent, private val start: Long, private val end: Long) : ResponseContent {

    override val length: Long? = end - start + 1

    override val type: ContentType?
        get() = source.type

    override val encoding: ByteBuffer?
        get() = source.encoding

    override suspend fun calculateChecksum(): Long? {
        var checksum = source.calculateChecksum()

        if (checksum != null) {
            checksum = checksum * 17 + start
            checksum = checksum * 17 + end
        }

        return checksum
    }

    override suspend fun write(sink: ResponseSink) {
        source.write(RangedSink(RangedStream(sink.stream, start, end)))
    }

}
