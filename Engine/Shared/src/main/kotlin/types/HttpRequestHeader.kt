package org.genhttp.engine.shared.types

import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.HttpProtocol
import org.genhttp.api.protocol.RequestHeader
import org.genhttp.api.protocol.RequestHeaders
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.RequestQuery
import org.genhttp.api.protocol.RequestTarget

import java.nio.ByteBuffer

/**
 * Header of a request, backed by the memory of the parsed request (`RequestHeader` in GenHTTP).
 */
public class HttpRequestHeader(private val request: HttpRequest) : RequestHeader {

    private val _headers = GlyphKeyValueList(request.source.headers)

    private val _query = GlyphKeyValueList(request.source.queryParameters)

    private val _target = HttpRequestTarget()

    private var _protocol: HttpProtocol = HttpProtocol.Http11

    private var _method: RequestMethod = RequestMethod.Get

    override val method: RequestMethod
        get() = _method

    override val path: ByteString
        get() = ByteString(request.source.path)

    override val target: RequestTarget
        get() = _target

    override val protocol: HttpProtocol
        get() = _protocol

    /**
     * The raw protocol version sent by the client.
     */
    public val version: ByteBuffer
        get() = request.source.version

    override val headers: RequestHeaders
        get() = _headers

    override val query: RequestQuery
        get() = _query

    /**
     * Initializes the header from the currently parsed request.
     */
    public fun apply() {
        _target.apply(path)
        _protocol = HttpProtocol(request.source.version)
        _method = RequestMethod(request.source.method)
    }

}
