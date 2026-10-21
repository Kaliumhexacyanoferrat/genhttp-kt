package org.genhttp.api.protocol

/**
 * Represents the header of an incoming HTTP request.
 */
public interface RequestHeader {

    /**
     * The protocol used by the client.
     */
    public val protocol: HttpProtocol

    /**
     * The request method (or verb).
     */
    public val method: RequestMethod

    /**
     * The requested path.
     */
    public val path: ByteString

    /**
     * The routable target, constructed from the path.
     */
    public val target: RequestTarget

    /**
     * The query sent by the client.
     */
    public val query: RequestQuery

    /**
     * The headers sent by the client.
     */
    public val headers: RequestHeaders

}
