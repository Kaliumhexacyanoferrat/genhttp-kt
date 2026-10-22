package org.genhttp.engine.internal.context

import org.genhttp.api.infrastructure.EndPoint
import org.genhttp.api.infrastructure.Server
import org.genhttp.engine.internal.infrastructure.ConnectionWatch
import org.genhttp.engine.internal.protocol.ClientHandler
import org.genhttp.engine.shared.io.ConnectionReader
import org.genhttp.engine.shared.io.ConnectionWriter
import org.genhttp.engine.shared.types.ClientContext
import org.genhttp.engine.shared.types.HttpRequest
import org.genhttp.engine.shared.types.ResponseHandler

import java.net.InetAddress
import java.nio.channels.ByteChannel
import java.nio.channels.Channel
import java.security.cert.X509Certificate

/**
 * The state of a single client connection (`ClientContext` in GenHTTP).
 *
 * @property connection The underlying connection (usually the socket, closed when the connection ends)
 * @property channel The channel used to exchange data (the socket itself or a TLS channel wrapping it)
 * @property watch The read deadline of the connection, enforced by the watchdog of the server
 * @property remoteAddress The address of the connected client, if known
 */
internal class ConnectionContext(
    override val server: Server,
    val endPoint: EndPoint,
    val connection: Channel,
    val channel: ByteChannel,
    val watch: ConnectionWatch,
    val clientCertificate: X509Certificate?,
    val remoteAddress: InetAddress?
) : ClientContext {

    val reader: ConnectionReader = ConnectionReader(channel)

    override val writer: ConnectionWriter = ConnectionWriter(channel)

    val request: HttpRequest = HttpRequest()

    val responseHandler: ResponseHandler = ResponseHandler(this)

    val clientHandler: ClientHandler = ClientHandler(this)

}
