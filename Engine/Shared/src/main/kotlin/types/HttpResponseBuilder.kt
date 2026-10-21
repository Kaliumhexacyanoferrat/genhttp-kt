package org.genhttp.engine.shared.types

import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.Connection
import org.genhttp.api.protocol.KeyValueList
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseBuilder
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseStatus

/**
 * Builds the responses of a connection (`ResponseBuilder` in GenHTTP).
 */
public class HttpResponseBuilder : ResponseBuilder {

    private val response = HttpResponse(this)

    override fun status(status: ResponseStatus): ResponseBuilder {
        response.status = status
        return this
    }

    override fun connection(mode: Connection): ResponseBuilder {
        response.mode = mode
        return this
    }

    override fun header(name: ByteString, value: ByteString): ResponseBuilder {
        response.editableHeaders.add(name, value)
        return this
    }

    override fun header(name: String, value: String): ResponseBuilder =
        header(ByteString(name.toByteArray(Charsets.US_ASCII)), ByteString(value.toByteArray(Charsets.US_ASCII)))

    override fun content(content: ResponseContent?): ResponseBuilder {
        if (content != null && response.status == ResponseStatus.NoContent) {
            response.status = ResponseStatus.Ok
        }

        response.content = content
        return this
    }

    override fun build(): Response = response

    /**
     * Resets the builder so it can be reused.
     */
    public fun reset() {
        response.reset()
    }

}
