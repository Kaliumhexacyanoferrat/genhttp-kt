package org.genhttp.modules.websockets.provider

import org.genhttp.api.content.Handler
import org.genhttp.api.content.ProviderException
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.Connection
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.api.protocol.getEntry
import org.genhttp.modules.websockets.protocol.Handshake

/**
 * Validates the websocket handshake and answers it with a response that upgrades the connection.
 *
 * @param contentFactory Creates the content that will handle the connection after the upgrade
 */
public class WebsocketHandler(private val contentFactory: (Request) -> ResponseContent) : Handler {

    override suspend fun prepare(server: Server) {
    }

    override suspend fun handle(request: Request): Response? {
        val key = validateRequest(request)

        val content = contentFactory(request)

        return request.respond()
            .status(ResponseStatus.SwitchingProtocols)
            .connection(Connection.Upgrade)
            .header(UpgradeHeaderName, UpgradeHeaderValue)
            .header(AcceptHeader, Handshake.createAcceptKey(key))
            .content(content)
            .build()
    }

    private fun validateRequest(request: Request): ByteString {
        val headers = request.header.headers

        if (request.header.method != RequestMethod.Get) {
            throw ProviderException(ResponseStatus.MethodNotAllowed, "Websocket connections can only be initiated via GET")
        }

        val key = headers.getEntry(KeyHeader)
            ?: throw ProviderException(ResponseStatus.BadRequest, "Client did not initiate websocket handshake. Header Sec-WebSocket-Key is missing from the request.")

        val version = headers.getEntry(VersionHeader)
            ?: throw ProviderException(ResponseStatus.BadGateway, "Client does not specify the requested websocket version.")

        if (version.toString().toIntOrNull() != 13) {
            throw ProviderException(ResponseStatus.UpgradeRequired, "Only version 13 is supported by this websocket server.") {
                header("Sec-WebSocket-Version", "13")
            }
        }

        return key
    }

    private companion object {

        private val KeyHeader = ByteString("Sec-WebSocket-Key")

        private val VersionHeader = ByteString("Sec-WebSocket-Version")

        private val AcceptHeader = ByteString("Sec-WebSocket-Accept")

        private val UpgradeHeaderName = ByteString("Upgrade")

        private val UpgradeHeaderValue = ByteString("websocket")

    }

}
