package org.genhttp.engine.netty.context

import io.netty.channel.Channel

import org.genhttp.api.infrastructure.EndPoint
import org.genhttp.api.infrastructure.Server
import org.genhttp.engine.netty.io.InboundChannel
import org.genhttp.engine.netty.io.OutboundChannel
import org.genhttp.engine.shared.io.ConnectionReader
import org.genhttp.engine.shared.io.ConnectionWriter
import org.genhttp.engine.shared.types.ClientContext
import org.genhttp.engine.shared.types.HttpRequest
import org.genhttp.engine.shared.types.ResponseHandler

import java.net.InetAddress
import java.security.cert.X509Certificate

/**
 * The state of a single client connection (`ClientContext` in GenHTTP).
 *
 * @property channel The Netty channel of the connection
 * @property inbound The data received from the client that has not been read yet
 * @property outbound Passes the written data to the channel
 * @property clientCertificate The certificate the client authenticated with, if any
 * @property remoteAddress The address of the connected client, if known
 */
internal class ConnectionContext(
    override val server: Server,
    val endPoint: EndPoint,
    val channel: Channel,
    val inbound: InboundChannel,
    val outbound: OutboundChannel,
    val clientCertificate: X509Certificate?,
    val remoteAddress: InetAddress?
) : ClientContext {

    /**
     * The reader of the connection, replaced by a new one after requests that required a larger buffer.
     */
    var reader: ConnectionReader = ConnectionReader(inbound)

    override val writer: ConnectionWriter = ConnectionWriter(outbound)

    /**
     * The request reused for all requests of the connection, replaced together with the [reader].
     */
    var request: HttpRequest = HttpRequest()

    val responseHandler: ResponseHandler = ResponseHandler(this)

}
