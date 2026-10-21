package org.genhttp.modules.io.providers

import org.genhttp.api.content.Handler
import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.io.streaming.ResourceContent

/**
 * A handler that responds with the content of the given resource.
 *
 * @param resource The resource to be served
 */
public class ContentProvider(public val resource: Resource) : Handler {

    private val content: ResponseContent = ResourceContent(resource)

    override suspend fun handle(request: Request): Response? =
        request.respond()
            .status(ResponseStatus.Ok)
            .content(content)
            .build()

    override suspend fun prepare(server: Server) {
    }

}
