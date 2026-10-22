package org.genhttp.engine.shared.types.body

import org.genhttp.api.content.ProviderException
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.engine.shared.io.ConnectionReader

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Provides the request body as a single block of memory.
 *
 * If the whole body is already buffered by the connection, the returned memory is a view on the
 * connection buffer (zero copy) that is released when the body is drained.
 */
public class MemoryConsumptionStrategy {

    private var length: Long? = null

    private var _reader: ConnectionReader? = null

    private var memory: ByteBuffer? = null

    private var pendingConsume = 0

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
        _reader = null
        length = null
        memory = null
        pendingConsume = 0
    }

    /**
     * Returns the memory representing the body.
     */
    public fun obtain(): ByteBuffer {
        memory?.let { return it }

        val length = length

        if (length != null) {
            // there is nothing to read, so do not wait for data that never arrives
            return if (length == 0L) EMPTY.also { memory = it } else readLength(length)
        }

        return readChunked()
    }

    private fun readLength(length: Long): ByteBuffer {
        if (length > Int.MAX_VALUE) {
            throw ProviderException(ResponseStatus.RequestEntityTooLarge, "Request body is too large to be read into memory")
        }

        val reader = reader
        val size = length.toInt()

        if (reader.available >= size) {
            pendingConsume = size
            return reader.data().slice(0, size).asReadOnlyBuffer().also { memory = it }
        }

        val bytes = try {
            reader.readExactly(size)
        } catch (e: IOException) {
            throw ProviderException(ResponseStatus.BadRequest, "Unexpected end of body", e)
        }

        return ByteBuffer.wrap(bytes).asReadOnlyBuffer().also { memory = it }
    }

    private fun readChunked(): ByteBuffer {
        val stream = ChunkedBodyStream(reader)
        val target = ByteArrayOutputStream()

        stream.transferTo(target)
        stream.drain()

        return ByteBuffer.wrap(target.toByteArray()).asReadOnlyBuffer().also { memory = it }
    }

    /**
     * Releases the memory read from the connection.
     */
    public fun drain() {
        if (pendingConsume > 0) {
            reader.consume(pendingConsume)
        }

        reset()
    }

    private companion object {

        val EMPTY: ByteBuffer = ByteBuffer.allocate(0).asReadOnlyBuffer()

    }

}
