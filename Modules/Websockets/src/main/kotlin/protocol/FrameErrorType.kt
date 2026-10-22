package org.genhttp.modules.websockets.protocol

/**
 * The kind of error that prevented a frame from being read.
 */
public enum class FrameErrorType {

    /**
     * No specific error.
     */
    None,

    /**
     * Not enough data has been received to decode a complete frame.
     */
    Incomplete,

    /**
     * The frame uses an unknown operation code.
     */
    InvalidOpCode,

    /**
     * The payload of the frame exceeds the maximum supported size.
     */
    PayloadTooLarge,

    /**
     * A control frame has been fragmented.
     */
    InvalidControlFrame,

    /**
     * A control frame exceeds the maximum length of control frames.
     */
    InvalidControlFrameLength,

    /**
     * The read operation has been canceled.
     */
    Canceled,

    /**
     * The connection input completed without a full frame being received.
     */
    IncompleteForever,

    /**
     * The frame could not be assembled.
     */
    UndefinedBehavior,

}
