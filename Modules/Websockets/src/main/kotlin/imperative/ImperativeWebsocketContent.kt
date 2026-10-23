package org.genhttp.modules.websockets.imperative

import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseSink
import org.genhttp.modules.websockets.ImperativeHandler
import org.genhttp.modules.websockets.provider.ConnectionSettings
import org.genhttp.modules.websockets.provider.WebsocketConnection

import java.nio.ByteBuffer

/**
 * The response content that hands the upgraded connection over to an imperative handler.
 *
 * @param handler The handler implementing the message pump
 * @param request The request that initiated the websocket connection
 * @param settings The settings of the connection
 */
public class ImperativeWebsocketContent(
    private val handler: ImperativeHandler,
    private val request: Request,
    private val settings: ConnectionSettings
) : ResponseContent {

    override val length: Long? = null

    override val type: ContentType? = null

    override val encoding: ByteBuffer? = null

    override suspend fun calculateChecksum(): Long? = null

    override suspend fun write(sink: ResponseSink) {
        sink.stream.flush()

        val connection = WebsocketConnection(request, sink, settings)

        connection.pump {
            handler.handle(connection)
        }
    }

}
