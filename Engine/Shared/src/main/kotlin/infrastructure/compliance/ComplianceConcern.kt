package org.genhttp.engine.shared.infrastructure.compliance

import org.genhttp.api.content.Concern
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.api.content.ProviderException
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.KnownHeaders
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.api.protocol.containsKey
import org.genhttp.api.protocol.getEntry

/**
 * Rejects requests that violate the HTTP specification in a way the parser cannot detect.
 */
public class ComplianceConcern(override val content: Handler) : Concern {

    override suspend fun prepare(server: Server): Unit = content.prepare(server)

    override suspend fun handle(request: Request): Response? {
        val header = request.header

        val host = header.headers.getEntry(KnownHeaders.Host)
            ?: throw ProviderException(ResponseStatus.BadRequest, "Host header is missing from the request")

        if (host.isEmpty) {
            throw ProviderException(ResponseStatus.BadRequest, "Host header is empty")
        }

        if (header.method == RequestMethod.Get || header.method == RequestMethod.Head) {
            if (header.headers.containsKey(KnownHeaders.ContentLength) || header.headers.containsKey(KnownHeaders.TransferEncoding)) {
                throw ProviderException(ResponseStatus.BadRequest, "GET/HEAD requests must not contain a body")
            }
        }

        return content.handle(request)
    }

}
