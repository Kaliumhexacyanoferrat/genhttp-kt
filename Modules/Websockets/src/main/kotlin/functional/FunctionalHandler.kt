package org.genhttp.modules.websockets.functional

import org.genhttp.modules.websockets.ReactiveConnection
import org.genhttp.modules.websockets.ReactiveHandler
import org.genhttp.modules.websockets.WebsocketFrame
import org.genhttp.modules.websockets.protocol.FrameError

/**
 * A reactive handler that forwards all events to the given functions.
 *
 * @param onConnected Invoked when a new client has connected
 * @param onMessage Invoked when a message frame has been received
 * @param onBinary Invoked when a binary message frame has been received
 * @param onContinue Invoked when a continuation frame has been received
 * @param onPing Invoked when a ping frame has been received
 * @param onPong Invoked when a pong frame has been received
 * @param onClose Invoked when the client requests to close the connection
 * @param onError Invoked if the server failed to read a frame from the connection
 */
public class FunctionalHandler(
    private val onConnected: suspend (ReactiveConnection) -> Unit,
    private val onMessage: suspend (ReactiveConnection, WebsocketFrame) -> Unit,
    private val onBinary: suspend (ReactiveConnection, WebsocketFrame) -> Unit,
    private val onContinue: suspend (ReactiveConnection, WebsocketFrame) -> Unit,
    private val onPing: suspend (ReactiveConnection, WebsocketFrame) -> Unit,
    private val onPong: suspend (ReactiveConnection, WebsocketFrame) -> Unit,
    private val onClose: suspend (ReactiveConnection, WebsocketFrame) -> Unit,
    private val onError: suspend (ReactiveConnection, FrameError) -> Boolean
) : ReactiveHandler {

    override suspend fun onConnected(connection: ReactiveConnection): Unit = onConnected.invoke(connection)

    override suspend fun onMessage(connection: ReactiveConnection, message: WebsocketFrame): Unit = onMessage.invoke(connection, message)

    override suspend fun onBinary(connection: ReactiveConnection, message: WebsocketFrame): Unit = onBinary.invoke(connection, message)

    override suspend fun onContinue(connection: ReactiveConnection, message: WebsocketFrame): Unit = onContinue.invoke(connection, message)

    override suspend fun onPing(connection: ReactiveConnection, message: WebsocketFrame): Unit = onPing.invoke(connection, message)

    override suspend fun onPong(connection: ReactiveConnection, message: WebsocketFrame): Unit = onPong.invoke(connection, message)

    override suspend fun onClose(connection: ReactiveConnection, message: WebsocketFrame): Unit = onClose.invoke(connection, message)

    override suspend fun onError(connection: ReactiveConnection, error: FrameError): Boolean = onError.invoke(connection, error)

}
