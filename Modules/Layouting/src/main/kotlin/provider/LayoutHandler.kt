package org.genhttp.modules.layouting.provider

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.PathSegment
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.modules.redirects.Redirect

/**
 * A handler that routes requests to other handlers by the segments of their path.
 *
 * @param routedHandlers The handlers to be invoked for the respective path segment
 * @param rootHandlers The fallback handlers to be invoked in order if no segment matched
 * @param index The handler to be invoked for requests to the layout itself
 */
public class LayoutHandler(routedHandlers: Map<PathSegment, Handler>, rootHandlers: List<Handler>, index: Handler?) : Handler {

    // region Get-/Setters

    /**
     * The handlers to be invoked for the respective path segment.
     */
    public val routedHandlers: Map<PathSegment, Handler> = HashMap(routedHandlers)

    /**
     * The fallback handlers to be invoked in order if no segment matched.
     */
    public val rootHandlers: Array<Handler> = rootHandlers.toTypedArray()

    /**
     * The handler to be invoked for requests to the layout itself.
     */
    public val index: Handler? = index

    // endregion

    // region Functionality

    override suspend fun prepare(server: Server) {
        for (routed in routedHandlers.values) {
            routed.prepare(server)
        }

        for (root in rootHandlers) {
            root.prepare(server)
        }

        index?.prepare(server)
    }

    override suspend fun handle(request: Request): Response? {
        val target = request.header.target

        val current = target.current

        if (current != null) {
            val handler = routedHandlers[current]

            if (handler != null) {
                target.advance()

                return handler.handle(request)
            }
        } else {
            // force a trailing slash to prevent duplicate content
            if (!target.hasTrailingSlash) {
                return Redirect.to("${target.asString(false)}/")
                    .build()
                    .handle(request)
            }

            if (index != null) {
                return index.handle(request)
            }
        }

        return invokeRootHandlers(request)
    }

    private suspend fun invokeRootHandlers(request: Request): Response? {
        for (handler in rootHandlers) {
            val result = handler.handle(request)

            if (result != null) {
                return result
            }
        }

        return null
    }

    // endregion

}
