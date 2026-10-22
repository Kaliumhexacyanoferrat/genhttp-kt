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

        val arguments = sink.arguments

        if (arguments == null && offset in WithoutArguments.indices) {
            return WithoutArguments[offset]
        }

        return RoutingMatch(offset, arguments)
    }

    // the matches of routes without path arguments are shared (by their offset)
    private val WithoutArguments = Array(4) { offset -> RoutingMatch(offset, null) }

}
