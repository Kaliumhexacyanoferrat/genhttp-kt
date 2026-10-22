package org.genhttp.modules.reverseproxy.provider

import org.genhttp.api.content.Handler
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.getEntry
import org.genhttp.modules.reverseproxy.http.HttpProxy
import org.genhttp.modules.reverseproxy.websocket.WebsocketProxy

import java.net.http.HttpClient

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Passes incoming requests to an upstream server, using either HTTP or the websocket protocol.
 *
 * @param upstream The URL of the server to pass requests to
 * @param client The client used to communicate with the upstream server
 * @param readTimeout The time allowed for the upstream server to generate a response (the JDK client
 * configures this per request instead of per client)
 */
public class ReverseProxyProvider(
    upstream: String,
    client: HttpClient,
    readTimeout: Duration = 60.seconds
) : Handler {

    private val httpProxy = HttpProxy(upstream, client, readTimeout)

    private val websocketProxy = WebsocketProxy(upstream)

    override suspend fun prepare(server: Server) {
    }

    override suspend fun handle(request: Request): Response? {
        val upgradeHeader = request.header.headers.getEntry(UpgradeHeader)

        if (upgradeHeader == WebsocketValue) {
            return websocketProxy.handle(request)
        }

        return httpProxy.handle(request)
    }

    private companion object {

        private val UpgradeHeader = ByteString("Upgrade")

        private val WebsocketValue = ByteString("websocket")

    }

}
