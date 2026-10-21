package org.genhttp.modules.io.providers

import org.genhttp.api.content.Concern
import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response

/**
 * A concern whose logic is provided by a function.
 *
 * @param content The handler wrapped by the concern
 * @param logic The logic to be executed
 */
public class InlineConcern(override val content: Handler, private val logic: suspend (Request, Handler) -> Response?) : Concern {

    override suspend fun prepare(server: Server) {
        content.prepare(server)
    }

    override suspend fun handle(request: Request): Response? = logic(request, content)

}
