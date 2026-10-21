package org.genhttp.modules.errorhandling.mappers

import org.genhttp.api.content.Handler
import org.genhttp.api.content.ProviderException
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.errorhandling.ErrorMapper
import org.genhttp.modules.io.apply
import org.genhttp.modules.pages.Renderer
import org.genhttp.modules.pages.getPage

/**
 * Renders exceptions as HTML pages in the style of the server.
 */
public class HtmlErrorMapper : ErrorMapper<Exception> {

    override suspend fun map(request: Request, handler: Handler, error: Exception, acceptedFormat: ByteString?): Response? {
        val developmentMode = request.server.development

        if (error is ProviderException) {
            val title = error.status.toString()

            val page = Renderer.server.render(title, error, developmentMode)

            return request.getPage(page)
                .status(error.status)
                .apply(error.modifications)
                .build()
        } else {
            val page = Renderer.server.render("Internal Server Error", error, developmentMode)

            return request.getPage(page)
                .status(ResponseStatus.InternalServerError)
                .build()
        }
    }

    override suspend fun getNotFound(request: Request, handler: Handler, acceptedFormat: ByteString?): Response? {
        val content = Renderer.server.render("Not Found", "The specified content was not found on this server.")

        return request.getPage(content)
            .status(ResponseStatus.NotFound)
            .build()
    }

}
