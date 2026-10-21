package org.genhttp.engine.shared.types

import org.genhttp.api.content.ProviderException
import org.genhttp.api.protocol.KnownHeaders
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.RequestBody
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.api.protocol.getEntry
import org.genhttp.engine.shared.io.ConnectionReader
import org.genhttp.engine.shared.types.body.ConsumptionStrategy
import org.genhttp.engine.shared.types.body.MemoryConsumptionStrategy
import org.genhttp.engine.shared.types.body.StreamConsumptionStrategy

import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Grants access to the body of a request, either as a stream or as memory (`RequestBody` in GenHTTP).
 */
public class HttpRequestBody(private val request: Request) : RequestBody {

    private var strategy: ConsumptionStrategy? = null

    private val streamStrategy = StreamConsumptionStrategy()

    private val memoryStrategy = MemoryConsumptionStrategy()

    private var _reader: ConnectionReader? = null

    /**
     * The length of the body as announced by the client, if known.
     */
    public var length: Long? = null
        private set

    internal val reader: ConnectionReader
        get() = _reader ?: throw IllegalStateException("Reader has not been initialized")

    // region Initialization

    /**
     * Prepares the body to be read from the given reader.
     */
    public fun apply(reader: ConnectionReader) {
        _reader = reader

        val headers = request.header.headers

        val contentLength = headers.getEntry(KnownHeaders.ContentLength)

        length = if (contentLength != null) {
            contentLength.toString().toLongOrNull()?.takeIf { it >= 0 }
                ?: throw ProviderException(ResponseStatus.BadRequest, "Content-Length header has an invalid value")
        } else {
            null
        }

        val transferEncoding = headers.getEntry(KnownHeaders.TransferEncoding)
        val chunked = transferEncoding != null && transferEncoding.bytes.mismatch(CHUNKED) == -1

        if (length == null && !chunked) {
            throw IllegalStateException("Request body has neither Content-Length nor Transfer-Encoding: chunked")
        }
    }

    /**
     * Resets the body so that the instance can be reused.
     */
    public fun reset() {
        strategy = null
        _reader = null
        streamStrategy.reset()
        memoryStrategy.reset()
    }

    // endregion

    // region Functionality

    override fun asStream(): InputStream {
        if (strategy == ConsumptionStrategy.Stream) {
            return streamStrategy.obtain()
        }

        setStrategy(ConsumptionStrategy.Stream)

        streamStrategy.apply(reader, length)
        return streamStrategy.obtain()
    }

    override suspend fun asMemory(): ByteBuffer {
        if (strategy == ConsumptionStrategy.Memory) {
            return memoryStrategy.obtain()
        }

        setStrategy(ConsumptionStrategy.Memory)

        memoryStrategy.apply(reader, length)
        return memoryStrategy.obtain()
    }

    private fun setStrategy(requested: ConsumptionStrategy) {
        val current = strategy

        if (current != null) {
            throw IllegalStateException("Cannot provide body as '$requested' as it has already been opened as '$current'")
        }

        strategy = requested
    }

    /**
     * Reads the remaining body from the connection.
     */
    public fun drain() {
        var current = strategy

        if (current == null) {
            // the user has ignored the body so we need to use a strategy ourselves
            asStream()
            current = ConsumptionStrategy.Stream
        }

        strategy = null

        when (current) {
            ConsumptionStrategy.Stream -> streamStrategy.drain()
            ConsumptionStrategy.Memory -> memoryStrategy.drain()
        }
    }

    // endregion

    private companion object {

        val CHUNKED: ByteBuffer = ByteBuffer.wrap("chunked".toByteArray(Charsets.US_ASCII)).asReadOnlyBuffer()

    }

}
