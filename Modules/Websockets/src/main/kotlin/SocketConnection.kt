package org.genhttp.modules.websockets

import org.genhttp.api.protocol.Request
import org.genhttp.modules.websockets.protocol.FrameType
import org.genhttp.modules.websockets.provider.ConnectionSettings

import java.nio.ByteBuffer

/**
 * A connection which can be used to write websocket messages
 * to the underlying stream.
 */
public interface SocketConnection {

    /**
     * The request that initially caused the web socket connection.
     */
    public val request: Request

    /**
     * The settings the connection was initialized with.
     */
    public val settings: ConnectionSettings

    /**
     * Creates a web socket frame from the given payload and options
     * and sends it to the client.
     *
     * @param payload The payload to be sent
     * @param opcode The kind of the frame to be sent
     * @param fin false, if you would like to break down large chunks of data into several frames (last one has to be sent with fin = true)
     * @param flush false to buffer the frame without flushing; call [flush] when done to send buffered frames in one go
     */
    public suspend fun write(payload: ByteBuffer, opcode: FrameType = FrameType.Text, fin: Boolean = true, flush: Boolean = true)

    /**
     * Sends a ping frame to the connected client.
     *
     * @param flush false to buffer the frame without flushing; call [flush] when done to send buffered frames in one go
     */
    public suspend fun ping(flush: Boolean = true)

    /**
     * Creates a pong frame with the given content and sends it to
     * the client.
     *
     * @param payload The payload to be sent
     * @param flush false to buffer the frame without flushing; call [flush] when done to send buffered frames in one go
     */
    public suspend fun pong(payload: ByteBuffer, flush: Boolean = true)

    /**
     * Creates a pong frame and sends it to the client.
     *
     * @param flush false to buffer the frame without flushing; call [flush] when done to send buffered frames in one go
     */
    public suspend fun pong(flush: Boolean = true)

    /**
     * Closes the web socket connection. After this method has been called,
     * the connection can no longer be used to send frames.
     *
     * @param reason The reason why the connection should be closed
     * @param statusCode The status code to be sent to the client
     * @param flush false to buffer the frame without flushing; call [flush] when done to send buffered frames in one go
     */
    public suspend fun close(reason: String? = null, statusCode: Int = 1000, flush: Boolean = true)

    /**
     * Flushes all buffered frames to the client. Only needed when frames were written with `flush = false`.
     */
    public suspend fun flush()

}
