package org.genhttp.modules.reflection.routing.segments

import org.genhttp.api.protocol.RequestTarget
import org.genhttp.modules.reflection.routing.PathArgumentSink
import org.genhttp.modules.reflection.routing.RoutingSegment

import java.nio.ByteBuffer

/**
 * A segment matching a fixed string (case-sensitive, like upstream).
 */
internal class StringSegment(segment: String) : RoutingSegment {

    private val segmentBytes: ByteBuffer = ByteBuffer.wrap(segment.toByteArray(Charsets.US_ASCII)).asReadOnlyBuffer()

    override val providedArguments: Array<String> = emptyArray()

    override fun tryMatch(target: RequestTarget, offset: Int, argumentSink: PathArgumentSink): Pair<Boolean, Int> {
        val next = target.next(offset)

        if (next != null && next.bytes.mismatch(segmentBytes) == -1) {
            return true to 1
        }

        return false to 0
    }

}
