package org.genhttp.modules.reflection.routing

import org.genhttp.api.protocol.RequestTarget

/**
 * A single part of the route of an operation (`IRoutingSegment` in GenHTTP).
 */
public interface RoutingSegment {

    /**
     * The names of the path arguments provided by this segment.
     */
    public val providedArguments: Array<String>

    /**
     * Checks whether the request target matches this segment.
     *
     * @param target The target of the current request
     * @param offset The offset of the segment within the remaining target
     * @param argumentSink Collects the path arguments read by the segment
     * @return Whether the segment matched and by how many segments the target needs to be advanced
     */
    public fun tryMatch(target: RequestTarget, offset: Int, argumentSink: PathArgumentSink): Pair<Boolean, Int>

}
