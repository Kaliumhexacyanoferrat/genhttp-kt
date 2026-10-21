package org.genhttp.modules.reflection.routing

import org.genhttp.api.protocol.RequestTarget

/**
 * Matches request targets against operation routes.
 */
internal object OperationRouter {

    fun tryMatch(target: RequestTarget, route: OperationRoute): RoutingMatch? {
        val segments = route.segments
        val sink = PathArgumentSink()

        var offset = 0

        for (i in segments.indices) {
            val (matched, offsetBy) = segments[i].tryMatch(target, i, sink)

            if (!matched) {
                return null
            }

            offset += offsetBy
        }

        return RoutingMatch(offset, sink.arguments)
    }

}
