package org.genhttp.modules.reflection.routing.segments

import org.genhttp.api.protocol.RequestTarget
import org.genhttp.modules.reflection.routing.PathArgumentSink
import org.genhttp.modules.reflection.routing.RoutingSegment
import org.genhttp.modules.reflection.routing.SegmentMatch

/**
 * Terminates a route, checking for trailing slashes and wildcard routes.
 */
public class ClosingSegment(private val forceTrailingSlash: Boolean, private val wildcard: Boolean) : RoutingSegment {

    override val providedArguments: Array<String> = emptyArray()

    override fun tryMatch(target: RequestTarget, offset: Int, argumentSink: PathArgumentSink): Pair<Boolean, Int> {
        val ended = target.next(offset) == null

        if (!ended) {
            return if (wildcard) SegmentMatch.Ended else SegmentMatch.None
        }

        if (target.hasTrailingSlash) {
            return SegmentMatch.Ended
        }

        if (forceTrailingSlash) {
            return SegmentMatch.None
        }

        return if (offset > 0) SegmentMatch.Previous else SegmentMatch.Ended
    }

}
