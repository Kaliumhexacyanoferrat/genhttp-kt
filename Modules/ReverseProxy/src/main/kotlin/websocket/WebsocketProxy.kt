package org.genhttp.modules.reverseproxy.websocket

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

import org.genhttp.api.content.Handler
import org.genhttp.api.content.ProviderException
import org.genhttp.api.infrastructure.Server
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.Response
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.websockets.provider.WebsocketHandler

import kotlin.time.Duration.Companion.milliseconds

/**
 * Tunnels websocket connections to an upstream server.
 *
 * @param upstream The URL of the server to pass requests to
 */
public class WebsocketProxy(private val upstream: String) : Handler {

    override suspend fun prepare(server: Server) {
    }

    override suspend fun handle(request: Request): Response? {
        val upstreamConnection = RawWebsocketConnection(upstream)

        try {
            upstreamConnection.initializeStream()

            try {
                withTimeout(ConnectionTimeout) {
                    upstreamConnection.tryUpgrade(request)
                }
            } catch (e: ProviderException) {
                throw e
            } catch (e: TimeoutCancellationException) {
                throw ProviderException(ResponseStatus.GatewayTimeout, "Connection to the upstream host timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw IllegalStateException("Failed to upgrade upstream.", e)
            }

            val websocketHandler = WebsocketHandler { WebsocketTunnelContent(upstreamConnection, it) }

            return websocketHandler.handle(request)
        } catch (e: Throwable) {
            upstreamConnection.close()
            throw e
        }
    }

    private companion object {

        // same as HttpClient
        private val ConnectionTimeout = 100_000.milliseconds

    }

}
