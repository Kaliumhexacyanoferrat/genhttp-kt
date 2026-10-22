package org.genhttp.modules.websockets.reactive

import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseSink
import org.genhttp.modules.websockets.ReactiveHandler
import org.genhttp.modules.websockets.protocol.FrameType
import org.genhttp.modules.websockets.provider.ConnectionSettings
import org.genhttp.modules.websockets.provider.WebsocketConnection

import java.nio.ByteBuffer

/**
 * The response content that runs a message pump on the upgraded connection and dispatches the received frames
 * to a reactive handler.
 *
 * @param handler The handler reacting to incoming frames
 * @param request The request that initiated the websocket connection
 * @param settings The settings of the connection
 */
public class ReactiveWebsocketContent(
    private val handler: ReactiveHandler,
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

        handler.onConnected(connection)

        while (request.server.running) {
            val frame = connection.readFrame() // ensures a frame is read

            val error = frame.isError()

            if (error != null) {
                if (handler.onError(connection, error)) {
                    handler.onClose(connection, frame)
                    break
                }

                continue
            }

            if (frame.type == FrameType.Close) {
                handler.onClose(connection, frame)
                break
            }

            when (frame.type) {
                FrameType.Text -> handler.onMessage(connection, frame)
                FrameType.Ping -> handler.onPing(connection, frame)
                FrameType.Pong -> handler.onPong(connection, frame)
                FrameType.Continue -> handler.onContinue(connection, frame)
                FrameType.Binary -> handler.onBinary(connection, frame)
                else -> {}
            }
        }
    }

}
