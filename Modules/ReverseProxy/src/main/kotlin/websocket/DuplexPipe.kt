package org.genhttp.modules.reverseproxy.websocket

import java.io.InputStream
import java.io.OutputStream

/**
 * A bidirectional channel consisting of an input to read from and an output to write to (the counterpart of
 * `IDuplexPipe`, which is not available on the JVM).
 *
 * @property input The stream to read data from
 * @property output The stream to write data to
 */
public class DuplexPipe(public val input: InputStream, public val output: OutputStream) : AutoCloseable {

    /**
     * Closes both directions of the pipe.
     */
    override fun close() {
        try {
            input.close()
        } finally {
            output.close()
        }
    }

}
