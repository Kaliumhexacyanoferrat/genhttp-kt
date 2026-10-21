package org.genhttp.engine.shared.types.body

import org.genhttp.engine.shared.io.ConnectionReader

import java.io.InputStream

/**
 * Provides the request body as a stream.
 */
public class StreamConsumptionStrategy {

    private var stream: InputStream? = null

    private var length: Long? = null

    private var _reader: ConnectionReader? = null

    internal val reader: ConnectionReader
        get() = _reader ?: throw IllegalStateException("Reader has not been initialized")

    /**
     * Prepares the strategy to read from the given reader.
     */
    public fun apply(reader: ConnectionReader, length: Long?) {
        _reader = reader
        this.length = length
    }

    /**
     * Resets the strategy so that it can be reused.
     */
    public fun reset() {
        stream = null
        _reader = null
        length = null
    }

    /**
     * Returns the stream representing the body.
     */
    public fun obtain(): InputStream {
        stream?.let { return it }

        val length = length

        return (if (length != null) LengthLimitedStream(reader, length) else ChunkedBodyStream(reader)).also { stream = it }
    }

    /**
     * Reads the remaining body from the connection.
     */
    public fun drain() {
        val current = obtain()

        reset()

        (current as? DrainableStream)?.drain()
    }

}
