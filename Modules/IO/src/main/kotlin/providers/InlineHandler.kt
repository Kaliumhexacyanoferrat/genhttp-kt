package org.genhttp.modules.io.providers

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response

/**
 * A handler whose logic is provided by a function.
 *
 * @param logic The logic to be executed
 */
public class InlineHandler(private val logic: suspend (Request) -> Response?) : Handler {

    override suspend fun prepare(server: Server) {
    }

    override suspend fun handle(request: Request): Response? = logic(request)

}
