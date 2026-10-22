package org.genhttp.modules.websockets

import org.genhttp.modules.websockets.protocol.FrameError
import org.genhttp.modules.websockets.protocol.FrameType

import java.nio.ByteBuffer

/**
 * A single frame read from a websocket connection.
 */
public interface WebsocketFrame {

    /**
     * The type of this frame.
     */
    public val type: FrameType

    /**
     * The data the frame consists of (a read-only view with the data between position and limit).
     *
     * Has to be read while the next frame has not been read from
     * the connection. Afterward this property is safe to be used,
     * independent of the connection lifecycle.
     */
    public val data: ByteBuffer

    /**
     * Provides additional, technical information about the frame as well
     * as access to the raw memory of the underlying buffer.
     *
     * While accessing [data] allocates the data into a buffer
     * so it can be accessed after the next frame has been read, the
     * raw view allows to directly access the memory as it has been
     * read from the underlying connection.
     */
    public val raw: RawFrameData

    /**
     * The connection the frame has been read from.
     */
    public val connection: SocketConnection

    /**
     * Checks, whether the frame represents an error.
     *
     * @return The actual error that happened, if this is an error frame, null otherwise
     */
    public fun isError(): FrameError?

}
