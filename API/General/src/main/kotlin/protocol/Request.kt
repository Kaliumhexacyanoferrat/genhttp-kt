package org.genhttp.api.protocol

import org.genhttp.api.infrastructure.ClientConnection
import org.genhttp.api.infrastructure.EndPoint
import org.genhttp.api.infrastructure.PropertyBag
import org.genhttp.api.infrastructure.Server

import java.io.InputStream

/**
 * Represents an incoming HTTP request to be handled by the server.
 */
public interface Request {

    /**
     * The server instance handling the request.
     */
    public val server: Server

    /**
     * The endpoint the request was sent to.
     */
    public val endPoint: EndPoint

    /**
     * The remote client that issued this request.
     */
    public val client: ClientConnection

    /**
     * Property bag to store values during the lifetime of the request.
     */
    public val properties: PropertyBag

    /**
     * The header of the HTTP request.
     */
    public val header: RequestHeader

    /**
     * Specifies whether this request has a body.
     */
    public val hasBody: Boolean

    /**
     * Attempts to fetch the body of the HTTP request (if any). Can only be called once.
     *
     * @param headerAccess Specifies access to the header should be retained or not
     * @return The body of the HTTP request (if any)
     */
    public fun getBody(headerAccess: HeaderAccess = HeaderAccess.Retain): RequestBody?

    /**
     * Registers a wrapper function that will be used to construct the actual request body instance when
     * requested. Allows concerns to transparently replace the request body to implement features such as
     * automatic request body decompression. Note, that only one wrapper can be active.
     *
     * @param wrapper The wrapper to apply when the body is retrieved
     */
    public fun wrapBody(wrapper: (RequestBody) -> RequestBody)

    /**
     * Creates a response for this request.
     *
     * @return The response which can be returned by handlers to answer this request
     */
    public fun respond(): ResponseBuilder

    /**
     * Expresses intention to upgrade the request to another protocol, granting access to the
     * underlying connection input used by the engine (including bytes the engine already buffered).
     *
     * Using the input in non-upgrade scenarios might cause the server failing to handle requests as intended.
     *
     * @return The underlying connection input
     */
    public fun upgrade(): InputStream

}
