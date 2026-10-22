package org.genhttp.modules.websockets.protocol

/**
 * The type of a websocket frame.
 */
public enum class FrameType {

    /**
     * No (or an unknown) type.
     */
    None,

    /**
     * A frame containing text data.
     */
    Text,

    /**
     * A frame containing binary data.
     */
    Binary,

    /**
     * A continuation of a previous, fragmented data frame.
     */
    Continue,

    /**
     * A control frame requesting to close the connection.
     */
    Close,

    /**
     * A ping control frame.
     */
    Ping,

    /**
     * A pong control frame.
     */
    Pong,

    /**
     * A frame that could not be read because of an error.
     */
    Error,

}
