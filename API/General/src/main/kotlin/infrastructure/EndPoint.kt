package org.genhttp.api.infrastructure

import java.net.InetAddress

/**
 * An endpoint the server will listen on for incoming requests.
 */
public interface EndPoint : AutoCloseable {

    /**
     * The IP address the endpoint is bound to.
     */
    public val address: InetAddress?

    /**
     * The port the endpoint is listening on.
     */
    public val port: Int

    /**
     * The HTTP protocols served over this endpoint.
     */
    public val protocols: HttpProtocols

    /**
     * If enabled, the endpoint will listen for both IPv4 and IPv6 connections.
     */
    public val dualStack: Boolean

    /**
     * Specifies, whether this is an endpoint secured via TLS.
     */
    public val secure: Boolean

}
