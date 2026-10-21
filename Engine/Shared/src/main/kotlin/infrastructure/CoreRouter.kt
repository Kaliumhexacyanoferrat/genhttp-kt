package org.genhttp.engine.shared.infrastructure

import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Concerns
import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response

/**
 * The root handler of a server, wrapping the user handler with the concerns installed by the engine.
 */
public class CoreRouter(content: Handler, concerns: Iterable<ConcernBuilder>) : Handler {

    /**
     * The outermost handler of the chain.
     */
    public val content: Handler = Concerns.chain(concerns, content)

    override suspend fun handle(request: Request): Response? = content.handle(request)

    override suspend fun prepare(server: Server): Unit = content.prepare(server)

}
