package org.genhttp.modules.websockets

/**
 * A connection used by the imperative handler to
 * fetch incoming frames from the underlying stream.
 */
public interface ImperativeConnection : SocketConnection {

    /**
     * Attempts to read the next websocket frame from the
     * underlying connection. Suspends until a frame is received.
     *
     * @return The next websocket frame to be handled
     */
    public suspend fun readFrame(): WebsocketFrame

    /**
     * Attempts to decode the next complete websocket frame from already-buffered data
     * without waiting for more data to arrive. Use this to drain multiple frames that
     * arrived in a single TCP segment before falling back to [readFrame].
     *
     * Returns individual RFC 6455 frame segments; continuation-frame assembly is not performed.
     *
     * @return The decoded frame if a complete frame was available; null if more data is needed
     */
    public fun tryReadFrame(): WebsocketFrame?

}
