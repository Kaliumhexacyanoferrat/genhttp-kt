package org.genhttp.modules.reflection.routing.segments

import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.RequestTarget
import org.genhttp.modules.reflection.routing.PathArgumentSink
import org.genhttp.modules.reflection.routing.RoutingSegment
import org.genhttp.modules.reflection.routing.SegmentMatch

/**
 * A segment that provides its whole value as a path argument (":name").
 */
internal class SimpleVariableSegment(variableName: String) : RoutingSegment {

    private val key = ByteString(variableName)

    override val providedArguments: Array<String> = arrayOf(variableName)

    override fun tryMatch(target: RequestTarget, offset: Int, argumentSink: PathArgumentSink): Pair<Boolean, Int> {
        val part = target.next(offset) ?: return SegmentMatch.None

        argumentSink.add(key, ByteString(part.bytes))

        return SegmentMatch.Consumed
    }

}
