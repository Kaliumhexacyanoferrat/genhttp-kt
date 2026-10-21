package org.genhttp.modules.reflection.routing.segments

import org.genhttp.api.protocol.RequestTarget
import org.genhttp.modules.reflection.routing.PathArgumentSink
import org.genhttp.modules.reflection.routing.RoutingSegment

/**
 * Terminates a route, checking for trailing slashes and wildcard routes.
 */
public class ClosingSegment(private val forceTrailingSlash: Boolean, private val wildcard: Boolean) : RoutingSegment {

    override val providedArguments: Array<String> = emptyArray()

    override fun tryMatch(target: RequestTarget, offset: Int, argumentSink: PathArgumentSink): Pair<Boolean, Int> {
        val ended = target.next(offset) == null

        if (!ended) {
            return if (wildcard) true to 0 else false to 0
        }

        if (target.hasTrailingSlash) {
            return true to 0
        }

        if (forceTrailingSlash) {
            return false to 0
        }

        return true to (if (offset > 0) -1 else 0)
    }

}
