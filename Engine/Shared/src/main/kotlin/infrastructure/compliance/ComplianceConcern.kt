package org.genhttp.engine.shared.infrastructure.compliance

import org.genhttp.api.content.Concern
import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.api.content.ProviderException
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.HttpProtocol
import org.genhttp.api.protocol.KnownHeaders
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.RequestHeader
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.api.protocol.containsKey
import org.genhttp.api.protocol.getEntry
import org.genhttp.engine.shared.types.body.ContinueReader

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

        if (request.hasBody) {
            checkExpectation(header)
        }

        return content.handle(request)
    }

    /**
     * Rejects any expectation other than 100-continue, which is the only one defined by HTTP (RFC 9110,
     * section 10.1.1).
     *
     * Requests without a body are not checked, as there is nothing for the client to wait for. The Expect header of
     * HTTP/1.0 requests is to be ignored.
     */
    private fun checkExpectation(header: RequestHeader) {
        if (header.protocol == HttpProtocol.Http10) {
            return
        }

        val expectation = header.headers.getEntry(KnownHeaders.Expect)

        if (expectation != null && !ContinueReader.isContinue(expectation)) {
            throw ProviderException(ResponseStatus.ExpectationFailed, "The only expectation supported is '100-continue'")
        }
    }

}
