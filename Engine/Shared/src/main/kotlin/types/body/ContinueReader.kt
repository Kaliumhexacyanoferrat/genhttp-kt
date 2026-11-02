package org.genhttp.engine.shared.types.body

import org.genhttp.api.protocol.ByteString
import org.genhttp.engine.shared.io.ConnectionWriter

/**
 * Holds back the body of a request whose client sent `Expect: 100-continue` until the client receives a
 * 100 (Continue) interim response (RFC 9110, section 10.1.1).
 *
 * The 100 is sent with the first read of the body, as Kestrel does, so a handler that answers without reading the
 * body (e.g. with a 401 or 413) never invites the upload. Once the final response has started, the reader is
 * disarmed, as no interim response may follow a final one.
 *
 * Upstream decorates the `PipeReader` of the connection; the framings of the port read from the buffer of the
 * connection directly and notify this instance via [beforeRead] wherever upstream reads from the pipe.
 */
internal class ContinueReader {

    private var writer: ConnectionWriter? = null

    // region Get-/Setters

    /**
     * Whether the 100 (Continue) has been sent to the client.
     */
    var sent: Boolean = false
        private set

    // endregion

    // region Initialization

    /**
     * Sends the 100 (Continue) to the given writer before the first read of the body.
     */
    fun arm(writer: ConnectionWriter) {
        this.writer = writer

        sent = false
    }

    /**
     * Ensures that the 100 (Continue) will not be sent anymore.
     */
    fun disarm() {
        writer = null
    }

    fun reset() {
        writer = null

        sent = false
    }

    // endregion

    // region Functionality

    /**
     * Invoked by the framings before they read from the connection (`ReadAsync` upstream), sends the
     * 100 (Continue) if it is still owed to the client.
     */
    fun beforeRead() {
        val writer = writer ?: return

        this.writer = null

        sent = true

        writer.write(Continue)
        writer.flush()
    }

    // endregion

    companion object {

        private val Continue = "HTTP/1.1 100 Continue\r\n\r\n".toByteArray(Charsets.US_ASCII)

        private val ContinueValue = ByteString("100-continue")

        /**
         * Checks whether the given expectation asks for a 100 (Continue).
         */
        fun isContinue(expectation: ByteString): Boolean = expectation == ContinueValue

    }

}
