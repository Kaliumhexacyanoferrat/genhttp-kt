package org.genhttp.modules.websockets

import java.nio.ByteBuffer

/**
 * Provides additional, technical information about a websocket frame.
 */
public interface RawFrameData {

    /**
     * Specifies, whether this is a websocket frame that has
     * been assembled from multiple parts.
     */
    public val isSegmented: Boolean

    /**
     * True, if this is either a single data frame,
     * a control frame or the end of a segmented data
     * stream when in manual continuation mode.
     */
    public val fin: Boolean

    /**
     * If this is a segmented frame, this property will hold
     * the non-allocated segments the frame is built from.
     */
    public val segments: List<ByteBuffer?>?

    /**
     * The data the frame consists of, as read from the underlying
     * connection (a read-only view with the data between position and limit).
     *
     * Using this property is only valid while the next frame
     * has not been read from the connection. Does not allocate (unless
     * the frame has been assembled from multiple segments).
     */
    public val memory: ByteBuffer

}
