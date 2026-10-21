package org.genhttp.engine.internal.context

import org.genhttp.api.infrastructure.EndPoint
import org.genhttp.api.infrastructure.Server
import org.genhttp.engine.internal.protocol.ClientHandler
import org.genhttp.engine.shared.io.ConnectionReader
import org.genhttp.engine.shared.io.ConnectionWriter
import org.genhttp.engine.shared.types.ClientContext
import org.genhttp.engine.shared.types.HttpRequest
import org.genhttp.engine.shared.types.ResponseHandler

import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.channels.ByteChannel
import java.nio.channels.SocketChannel
import java.security.cert.X509Certificate

/**
 * The state of a single client connection (`ClientContext` in GenHTTP).
 *
 * @property connection The underlying socket of the connection
 * @property channel The channel used to exchange data (the socket itself or a TLS channel wrapping it)
 */
internal class ConnectionContext(
    override val server: Server,
    val endPoint: EndPoint,
    val connection: SocketChannel,
    val channel: ByteChannel,
    val clientCertificate: X509Certificate?
) : ClientContext {

    val reader: ConnectionReader = ConnectionReader(channel)

    override val writer: ConnectionWriter = ConnectionWriter(channel)

    val request: HttpRequest = HttpRequest()

    val responseHandler: ResponseHandler = ResponseHandler(this)

    val clientHandler: ClientHandler = ClientHandler(this)

    val remoteAddress: InetAddress?
        get() = try {
            (connection.remoteAddress as? InetSocketAddress)?.address
        } catch (_: Exception) {
            null
        }

}
