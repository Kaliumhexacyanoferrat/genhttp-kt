package org.genhttp.engine.shared.types

import org.genhttp.api.infrastructure.ClientConnection
import org.genhttp.api.infrastructure.EndPoint
import org.genhttp.api.infrastructure.PropertyBag
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.ClientProtocol
import org.genhttp.api.protocol.HeaderAccess
import org.genhttp.api.protocol.KnownHeaders
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.RequestBody
import org.genhttp.api.protocol.RequestHeader
import org.genhttp.api.protocol.ResponseBuilder
import org.genhttp.api.protocol.containsKey
import org.genhttp.engine.shared.io.ConnectionReader
import org.genhttp.glyph11.protocol.BinaryRequest

import java.io.InputStream
import java.net.InetAddress
import java.security.cert.X509Certificate

/**
 * A request handled by an engine, reused for all requests of a connection (`Request` in GenHTTP).
 */
public class HttpRequest : Request {

    private var retainedHeader: RequestHeader? = null

    private var _server: Server? = null

    private var _endPoint: EndPoint? = null

    private var _reader: ConnectionReader? = null

    private var wrappedBody: RequestBody? = null

    private var bodyWrapper: ((RequestBody) -> RequestBody)? = null

    private val _header: HttpRequestHeader

    private val _client = RemoteClientConnection()

    private val _properties = MapPropertyBag()

    private val response = HttpResponseBuilder()

    private val body: HttpRequestBody

    private var bodyLoaded = false

    private var _hasBody = false

    private var resetRequired = true

    // region Get-/Setters

    /**
     * The request parsed by glyph11 this instance reads from.
     */
    public val source: BinaryRequest = BinaryRequest()

    /**
     * The reader of the connection the request has been received on.
     */
    public val reader: ConnectionReader
        get() = _reader ?: throw IllegalStateException("Reader property has not been initialized")

    override val server: Server
        get() = _server ?: throw IllegalStateException("Server property has not been initialized")

    override val endPoint: EndPoint
        get() = _endPoint ?: throw IllegalStateException("EndPoint property has not been initialized")

    override val client: ClientConnection
        get() = _client

    override val properties: PropertyBag
        get() = _properties

    override val header: RequestHeader
        get() {
            if (!bodyLoaded) {
                return retainedHeader ?: _header
            }

            return retainedHeader ?: throw IllegalStateException("Header information can no longer be accessed")
        }

    override val hasBody: Boolean
        get() = _hasBody

    // endregion

    init {
        _header = HttpRequestHeader(this)
        body = HttpRequestBody(this)
    }

    // region Functionality

    override fun getBody(headerAccess: HeaderAccess): RequestBody? {
        if (bodyLoaded) {
            throw IllegalStateException("Request body can only be fetched once.")
        }

        if (headerAccess == HeaderAccess.Retain && retainedHeader == null) {
            retainedHeader = RetainedRequestHeader(_header)
        }

        if (_hasBody) {
            body.apply(reader)
        }

        bodyLoaded = true

        if (!_hasBody) {
            return null
        }

        val wrapper = bodyWrapper

        if (wrapper != null) {
            return wrapper(body).also { wrappedBody = it }
        }

        return body
    }

    /**
     * Initializes the request from the request that has just been parsed into [source].
     */
    public fun apply(server: Server, endPoint: EndPoint, reader: ConnectionReader, remoteAddress: InetAddress?, clientCertificate: X509Certificate?) {
        _server = server
        _endPoint = endPoint
        _reader = reader

        _header.apply()
        _client.apply(remoteAddress, if (endPoint.secure) ClientProtocol.Https else ClientProtocol.Http, clientCertificate)
        _properties.clear()

        bodyLoaded = false
        retainedHeader = null
        wrappedBody = null
        bodyWrapper = null

        val headers = _header.headers
        _hasBody = headers.containsKey(KnownHeaders.ContentLength) || headers.containsKey(KnownHeaders.TransferEncoding)
    }

    /**
     * Initializes the request for a server that does not read from a connection (e.g. for tests).
     */
    public fun apply(server: Server) {
        _server = server
        _header.apply()
        _client.apply(null, null, null)
        _properties.clear()

        bodyLoaded = false
        _hasBody = false
        retainedHeader = null
        wrappedBody = null
        bodyWrapper = null
    }

    /**
     * Resets the request so that it can be reused for the next request on the connection.
     */
    public fun reset() {
        source.clear()
        response.reset()
        body.reset()
        _client.reset()

        (wrappedBody as? AutoCloseable)?.close()

        wrappedBody = null
        bodyWrapper = null
        resetRequired = true
    }

    /**
     * Reads the remaining body of the request (if any), so the next request can be read from the connection.
     */
    public fun drain() {
        if (bodyLoaded) {
            if (_hasBody) {
                body.drain()
            }

            return
        }

        if (getBody(HeaderAccess.Release) != null) {
            body.drain()
        }
    }

    override fun respond(): ResponseBuilder {
        if (!resetRequired) {
            response.reset()
        } else {
            resetRequired = false
        }

        return response
    }

    override fun wrapBody(wrapper: (RequestBody) -> RequestBody) {
        bodyWrapper = wrapper
    }

    override fun upgrade(): InputStream {
        // the upgraded connection outlives the buffer the header was parsed from
        if (retainedHeader == null) {
            retainedHeader = RetainedRequestHeader(_header)
        }

        bodyLoaded = true
        _hasBody = false

        return reader.asInputStream()
    }

    // endregion

}
