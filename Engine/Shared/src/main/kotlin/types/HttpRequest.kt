package org.genhttp.engine.shared.types

import org.genhttp.api.infrastructure.ClientConnection
import org.genhttp.api.infrastructure.EndPoint
import org.genhttp.api.infrastructure.PropertyBag
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.ClientProtocol
import org.genhttp.api.protocol.HeaderAccess
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.RequestBody
import org.genhttp.api.protocol.RequestHeader
import org.genhttp.api.protocol.ResponseBuilder
import org.genhttp.engine.shared.io.ConnectionReader
import org.genhttp.engine.shared.io.ConnectionWriter
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

    private val _header: HttpRequestHeader

    private val _client = RemoteClientConnection()

    private val _properties = MapPropertyBag()

    private val response = HttpResponseBuilder()

    private val body: HttpRequestBody

    private var resetRequired = true

    // region Get-/Setters

    /**
     * The request parsed by glyph11 this instance reads from.
     */
    public val source: BinaryRequest = BinaryRequest()

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
            if (!body.isOpened) {
                return retainedHeader ?: _header
            }

            return retainedHeader ?: throw IllegalStateException("Header information can no longer be accessed")
        }

    override val hasBody: Boolean
        get() = body.isPresent

    // endregion

    init {
        _header = HttpRequestHeader(this)
        body = HttpRequestBody(_header)
    }

    // region Functionality

    override fun getBody(headerAccess: HeaderAccess): RequestBody? {
        if (body.isOpened) {
            throw IllegalStateException("Request body can only be fetched once.")
        }

        if (headerAccess == HeaderAccess.Retain && retainedHeader == null) {
            retainedHeader = RetainedRequestHeader(_header)
        }

        return body.open()
    }

    /**
     * Initializes the request from the request that has just been parsed into [source].
     *
     * @param writer The writer of the connection, used to answer a client that expects a 100 (Continue); null, if
     *               the engine does not support interim responses
     */
    public fun apply(server: Server, endPoint: EndPoint, reader: ConnectionReader, remoteAddress: InetAddress?, clientCertificate: X509Certificate?, writer: ConnectionWriter?) {
        _server = server
        _endPoint = endPoint

        _header.apply()
        _client.apply(remoteAddress, if (endPoint.secure) ClientProtocol.Https else ClientProtocol.Http, clientCertificate)
        _properties.clear()

        retainedHeader = null

        body.apply(reader, writer)
    }

    /**
     * Initializes the request for a server that does not read from a connection (e.g. for tests).
     */
    public fun apply(server: Server) {
        _server = server
        _header.apply()
        _client.apply(null, null, null)
        _properties.clear()

        retainedHeader = null

        body.apply()
    }

    /**
     * Resets the request so that it can be reused for the next request on the connection.
     */
    public fun reset() {
        source.clear()
        response.reset()
        body.reset()
        _client.reset()

        resetRequired = true
    }

    /**
     * Invoked by the engine right before the final response to this request is written to the connection.
     */
    public fun responseStarting() {
        body.responseStarting()
    }

    /**
     * Invoked by the engine after the response has been written to consume whatever is left of the request body.
     *
     * @return true, if the connection can be used to serve another request
     */
    public suspend fun finish(): Boolean = body.finish()

    override fun respond(): ResponseBuilder {
        if (!resetRequired) {
            response.reset()
        } else {
            resetRequired = false
        }

        return response
    }

    override fun wrapBody(wrapper: (RequestBody) -> RequestBody) {
        body.wrap(wrapper)
    }

    override fun upgrade(): InputStream {
        // the upgraded connection outlives the buffer the header was parsed from
        if (!body.isOpened && retainedHeader == null) {
            retainedHeader = RetainedRequestHeader(_header)
        }

        return body.upgrade().asInputStream()
    }

    // endregion

}
