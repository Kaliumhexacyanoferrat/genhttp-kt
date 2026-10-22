package org.genhttp.modules.loadbalancing.provider

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.modules.redirects.Redirect

/**
 * A handler that redirects the client to the same path on another root location.
 *
 * @param root The root location to redirect the client to
 */
public class LoadBalancerRedirectionHandler(root: String) : Handler {

    private val root: String = if (root.endsWith('/')) root else "$root/"

    // region Functionality

    override suspend fun handle(request: Request): Response? {
        val current = request.header.target.current

        val appendix = current?.toString() ?: ""

        return Redirect.to(root + appendix, true)
            .build()
            .handle(request)
    }

    override suspend fun prepare(server: Server) {
    }

    // endregion

}
