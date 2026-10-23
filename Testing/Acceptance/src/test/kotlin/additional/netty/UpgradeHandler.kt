package org.genhttp.testing.acceptance.additional.netty

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Connection
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseSink
import org.genhttp.api.protocol.ResponseStatus

import java.io.IOException
import java.net.Socket
import java.nio.ByteBuffer

/**
 * Upgrades every connection to a custom protocol and passes the upgraded connection to the given function (like the
 * websocket tunnel of the reverse proxy, which reads the upgraded connection with blocking reads).
 *
 * @param serve Serves the upgraded connection: reads from `request.upgrade()`, writes to the sink
 */
class UpgradeHandler(private val serve: suspend (Request, ResponseSink) -> Unit) : Handler {

    override suspend fun prepare(server: Server) {
    }

    override suspend fun handle(request: Request): Response =
        request.respond()
            .status(ResponseStatus.SwitchingProtocols)
            .connection(Connection.Upgrade)
            .header("Upgrade", Protocol)
            .content(UpgradedContent(request, serve))
            .build()

    private class UpgradedContent(private val request: Request, private val serve: suspend (Request, ResponseSink) -> Unit) : ResponseContent {

        override val length: Long? = null

        override val type: ContentType? = null

        override val encoding: ByteBuffer? = null

        override suspend fun calculateChecksum(): Long? = null

        override suspend fun write(sink: ResponseSink) {
            // sends the response header
            sink.stream.flush()

            serve(request, sink)
        }

    }

    companion object {

        private const val Protocol = "raw"

        /**
         * Sends an upgrade request on the given socket and reads the response header.
         */
        fun upgrade(socket: Socket, path: String = "/") {
            RawClient.send(socket, RawClient.request(path, headers = listOf("Connection" to "Upgrade", "Upgrade" to Protocol)))

            val head = RawClient.readHead(socket.getInputStream())

            if (head.status != 101) {
                throw IOException("The server did not upgrade the connection (${head.status})")
            }
        }

    }

}
