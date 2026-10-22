package org.genhttp.modules.virtualhosting.provider

import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.modules.io.getHostWithoutPort

/**
 * A handler that routes requests to other handlers by the host name sent by the client.
 *
 * @param hosts The handlers to be invoked for the registered hosts
 * @param defaultRoute The handler to be invoked if no host matches
 */
public class VirtualHostRouter(hosts: Map<ByteString, HandlerBuilder>, defaultRoute: HandlerBuilder?) : Handler {

    // region Get-/Setters

    private val hosts: Map<ByteString, Handler> = hosts.mapValues { it.value.build() }

    private val defaultRoute: Handler? = defaultRoute?.build()

    // endregion

    // region Functionality

    override suspend fun prepare(server: Server) {
        for (host in hosts.values) {
            host.prepare(server)
        }

        defaultRoute?.prepare(server)
    }

    override suspend fun handle(request: Request): Response? = getHandler(request)?.handle(request)

    private fun getHandler(request: Request): Handler? {
        val host = request.getHostWithoutPort()

        // try to find a regular route
        if (host != null) {
            val handler = hosts[host]

            if (handler != null) {
                return handler
            }
        }

        // route by default
        return defaultRoute
    }

    // endregion

}
