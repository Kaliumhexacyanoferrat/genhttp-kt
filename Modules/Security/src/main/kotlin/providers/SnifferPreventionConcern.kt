package org.genhttp.modules.security.providers

import org.genhttp.api.content.Concern
import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response

/**
 * Prevents browsers from guessing the content type of responses by adding the `X-Content-Type-Options` header.
 *
 * @param content The handler to be wrapped by this concern
 */
public class SnifferPreventionConcern(override val content: Handler) : Concern {

    // region Functionality

    override suspend fun handle(request: Request): Response? {
        val response = content.handle(request)

        response?.rebuild()?.header(ContentTypeOptions, NoSniffValue)

        return response
    }

    override suspend fun prepare(server: Server) {
        content.prepare(server)
    }

    // endregion

    private companion object {

        private val ContentTypeOptions = ByteString("X-Content-Type-Options")

        private val NoSniffValue = ByteString("nosniff")

    }

}
