package org.genhttp.modules.websockets

import org.genhttp.modules.websockets.protocol.FrameError

/**
 * A handler that can be passed to the reactive web socket
 * implementation to handle incoming websocket frames.
 */
public interface ReactiveHandler {

    /**
     * Invoked when a new client has connected.
     *
     * @param connection The connection to the client
     */
    public suspend fun onConnected(connection: ReactiveConnection) {
    }

    /**
     * Invoked when a message frame has been received.
     *
     * @param connection The connection to the client
     * @param message The incoming message frame
     */
    public suspend fun onMessage(connection: ReactiveConnection, message: WebsocketFrame) {
    }

    /**
     * Invoked when a binary message frame has been received.
     *
     * @param connection The connection to the client
     * @param message The incoming binary message frame
     */
    public suspend fun onBinary(connection: ReactiveConnection, message: WebsocketFrame) {
    }

    /**
     * Invoked when the client splits frames into multiple messages
     * and an additional frame after the first has been received.
     *
     * @param connection The connection to the client
     * @param message The incoming message frame
     */
    public suspend fun onContinue(connection: ReactiveConnection, message: WebsocketFrame) {
    }

    /**
     * Invoked when a ping frame has been received.
     *
     * @param connection The connection to the client
     * @param message The incoming ping frame
     */
    public suspend fun onPing(connection: ReactiveConnection, message: WebsocketFrame) {
        connection.pong(message.data)
    }

    /**
     * Invoked when a pong frame has been received.
     *
     * @param connection The connection to the client
     * @param message The incoming pong frame
     */
    public suspend fun onPong(connection: ReactiveConnection, message: WebsocketFrame) {
    }

    /**
     * Invoked when the client requests to close the connection.
     *
     * @param connection The connection to the client
     * @param message The frame sent by the client
     */
    public suspend fun onClose(connection: ReactiveConnection, message: WebsocketFrame) {
        connection.close()
    }

    /**
     * Invoked if the server failed to read a frame from the connection.
     *
     * @param connection The connection to the client
     * @param error The error which ocurred
     * @return true, if the connection should be closed
     */
    public suspend fun onError(connection: ReactiveConnection, error: FrameError): Boolean = true

}
