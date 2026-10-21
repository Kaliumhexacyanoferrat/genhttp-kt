package org.genhttp.api.protocol

import java.net.InetAddress

/**
 * Stores information on how a request has been proxied to the server, as indicated by a single hop of
 * a "Forwarded" header (RFC 7239) or a legacy "X-Forwarded-*" header set.
 *
 * @property forAddress The client that initiated the request, as seen by the proxy that added this entry
 * (`For` in GenHTTP, renamed as `for` is a keyword in Kotlin)
 * @property by The interface of the proxy that added this entry
 * @property host The host requested by the client
 * @property protocol The protocol used by the client to connect to the proxy
 */
public data class Forwarding(
    val forAddress: InetAddress?,
    val by: InetAddress?,
    val host: String?,
    val protocol: ClientProtocol?
)
