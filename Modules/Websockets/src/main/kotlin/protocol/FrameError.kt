package org.genhttp.modules.websockets.protocol

/**
 * Describes an error that happened while reading a frame from the connection.
 *
 * @param message A description of the error
 * @param errorType The kind of error that happened
 */
public data class FrameError(val message: String, val errorType: FrameErrorType = FrameErrorType.None) {

    public companion object {

        /**
         * The message used if a read operation has been canceled.
         */
        public const val ReadCanceled: String = "Read was canceled"

        /**
         * The message used if the connection ended while a frame was being read.
         */
        public const val UnexpectedEndOfStream: String = "Unexpected end of stream while reading WebSocket frame."

    }

}
