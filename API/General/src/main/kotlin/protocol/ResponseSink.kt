package org.genhttp.api.protocol

import java.io.OutputStream
import java.nio.channels.WritableByteChannel

/**
 * Exposes channels that can be used by response content instances to write data to the response body.
 */
public interface ResponseSink {

    /**
     * A channel which can be used to write the response body.
     *
     * Writes are buffered by the engine; use this channel to efficiently pass data that is already available
     * in memory. The channel must not be closed by the content.
     */
    public val writer: WritableByteChannel

    /**
     * A write-only stream which can be used to write the response body.
     *
     * The stream must not be closed by the content. Flushing the stream will send the data written so far to
     * the client.
     */
    public val stream: OutputStream

}
