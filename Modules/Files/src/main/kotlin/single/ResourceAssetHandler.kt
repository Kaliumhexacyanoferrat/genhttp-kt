package org.genhttp.modules.files.single

import org.genhttp.api.content.Handler
import org.genhttp.api.content.ProviderException
import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.io.hasType
import org.genhttp.modules.io.streaming.ResourceContent

/**
 * A handler that serves a single resource.
 *
 * @param resource The resource to be served
 * @param asDownload Whether the client should be instructed to download the resource
 * @param fileName The name of the file to be suggested to the client (defaults to the name of the resource)
 * @param contentType The type of the content (guessed from the resource if not specified)
 */
public class ResourceAssetHandler(
    private val resource: Resource,
    private val asDownload: Boolean,
    private val fileName: String?,
    private val contentType: ContentType?
) : Handler {

    override suspend fun prepare(server: Server) {
    }

    override suspend fun handle(request: Request): Response? {
        val target = request.header.target

        if (target.current != null) {
            return null
        }

        if (!request.hasType(RequestMethod.Get, RequestMethod.Head)) {
            throw ProviderException(ResponseStatus.MethodNotAllowed, "Only GET requests are allowed by this handler") {
                header("Allow", "GET")
            }
        }

        val response = request.respond()
            .content(ResourceContent(resource, contentType))

        if (asDownload) {
            val name = fileName ?: resource.name

            if (name != null) {
                response.header("Content-Disposition", "attachment; filename=\"$name\"")
            } else {
                response.header("Content-Disposition", "attachment")
            }
        }

        return response.build()
    }

}
