package org.genhttp.modules.reflection

import org.genhttp.api.content.Handler
import org.genhttp.api.content.ProviderException
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.reflection.routing.OperationRouter
import org.genhttp.modules.reflection.routing.RoutingMatch

/**
 * Routes requests to the operations of a service.
 *
 * @param methods The handlers of the operations of the service
 */
public class MethodCollection(methods: Iterable<MethodHandler>) : Handler {

    /**
     * The handlers of the operations of the service.
     */
    public val methods: List<MethodHandler> = methods.toList()

    override suspend fun handle(request: Request): Response? {
        var foundOthers = false

        var directMatch: Pair<MethodHandler, RoutingMatch>? = null
        var wildcardMatch: Pair<MethodHandler, RoutingMatch>? = null

        val requestTarget = request.header.target

        for (method in methods) {
            val operation = method.operation
            val route = operation.route

            val match = OperationRouter.tryMatch(requestTarget, route) ?: continue

            if (!operation.configuration.supportedMethods.contains(request.header.method)) {
                foundOthers = true
                continue
            }

            if (route.isWildcard) {
                wildcardMatch = method to match
            } else if (directMatch != null) {
                throw ProviderException(ResponseStatus.BadRequest, "There are multiple methods matching '${requestTarget.asString(decode = false)}'")
            } else {
                directMatch = method to match
            }
        }

        directMatch?.let { return it.first.handle(request, it.second) }

        wildcardMatch?.let { return it.first.handle(request, it.second) }

        if (foundOthers) {
            throw ProviderException(ResponseStatus.MethodNotAllowed, "There is no method of a matching request type")
        }

        return null
    }

    override suspend fun prepare(server: Server) {
        for (handler in methods) {
            handler.prepare(server)
        }
    }

}
