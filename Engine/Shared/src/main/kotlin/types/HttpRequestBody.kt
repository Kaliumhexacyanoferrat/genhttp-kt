package org.genhttp.engine.shared.types

import org.genhttp.api.content.ProviderException
import org.genhttp.api.protocol.KnownHeaders
import org.genhttp.api.protocol.RequestBody
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.api.protocol.containsKey
import org.genhttp.api.protocol.getEntry
import org.genhttp.engine.shared.io.ConnectionReader
import org.genhttp.engine.shared.types.body.BodyFraming
import org.genhttp.engine.shared.types.body.BodyState
import org.genhttp.engine.shared.types.body.BodyStream
import org.genhttp.engine.shared.types.body.ChunkedFraming
import org.genhttp.engine.shared.types.body.ContentLengthFraming

import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Owns the lifecycle of the body of a request, from the point the header has been parsed until the connection is
 * ready for the next request (`RequestBody` in GenHTTP).
 *
 * The body can be read as a stream and as memory, the second access continues where the first one left off.
 */
public class HttpRequestBody internal constructor(private val header: HttpRequestHeader) : RequestBody {

    private val contentLength = ContentLengthFraming()

    private val chunked = ChunkedFraming()

    private var _reader: ConnectionReader? = null

    private var state = BodyState.Pending

    private var present = false

    private var _framing: BodyFraming? = null

    private var stream: InputStream? = null

    private var memory: ByteBuffer? = null

    private var wrapper: ((RequestBody) -> RequestBody)? = null

    private var wrapped: RequestBody? = null

    // region Get-/Setters

    /**
     * The length of the body as declared by the client, if known.
     */
    public var length: Long? = null
        private set

    internal val isPresent: Boolean
        get() = present

    internal val isOpened: Boolean
        get() = state != BodyState.Pending

    private val reader: ConnectionReader
        get() = _reader ?: throw IllegalStateException("Reader has not been initialized")

    private val framing: BodyFraming
        get() = _framing ?: throw IllegalStateException("Request body has not been opened")

    // endregion

    // region Initialization

    internal fun apply(reader: ConnectionReader) {
        _reader = reader
        state = BodyState.Pending

        wrapper = null
        wrapped = null

        val headers = header.headers

        present = headers.containsKey(KnownHeaders.ContentLength) || headers.containsKey(KnownHeaders.TransferEncoding)
    }

    internal fun apply() {
        clear()
    }

    internal fun reset() {
        (wrapped as? AutoCloseable)?.close()

        clear()
    }

    private fun clear() {
        _reader = null
        state = BodyState.Pending
        present = false

        _framing?.let {
            it.reset()
            _framing = null
        }

        length = null

        stream = null
        memory = null

        wrapper = null
        wrapped = null
    }

    // endregion

    // region Lifecycle

    /**
     * Moves past the header and provides access to the body, if any.
     */
    internal fun open(): RequestBody? {
        if (present) {
            resolveFraming()
        }

        release()

        if (!present) {
            return null
        }

        val wrapper = wrapper

        if (wrapper != null) {
            return wrapper(this).also { wrapped = it }
        }

        return this
    }

    internal fun wrap(wrapper: (RequestBody) -> RequestBody) {
        this.wrapper = wrapper
    }

    /**
     * Hands the connection over to another protocol.
     */
    internal fun upgrade(): ConnectionReader {
        val reader = reader

        release()

        state = BodyState.Upgraded
        present = false

        return reader
    }

    /**
     * Consumes whatever is left of the body, so that the connection is positioned at the start of the next request.
     *
     * @return true, if the connection can be used for another request
     */
    internal fun finish(): Boolean {
        if (state == BodyState.Upgraded) {
            return false
        }

        if (!present) {
            release()
            return true
        }

        val framing = _framing ?: tryResolveUnopened() ?: return false

        return framing.skip()
    }

    /**
     * Resolves the framing of a body nobody has opened, so it can be skipped.
     *
     * @return null, if the framing is invalid and the body cannot be skipped
     */
    private fun tryResolveUnopened(): BodyFraming? {
        val framing = try {
            resolveFraming()
        } catch (_: ProviderException) {
            return null
        }

        release()

        return framing
    }

    /**
     * The header has been consumed from the reader by the engine already, its views stay valid until the body
     * is read (see [HttpRequest.getBody]), so the body only needs to be marked as opened.
     */
    private fun release() {
        if (state == BodyState.Pending) {
            state = BodyState.Opened
        }
    }

    private fun resolveFraming(): BodyFraming {
        val headers = header.headers

        val contentLengthHeader = headers.getEntry(KnownHeaders.ContentLength)

        if (contentLengthHeader != null) {
            val declared = contentLengthHeader.toString().toLongOrNull()?.takeIf { it >= 0 }
                ?: throw ProviderException(ResponseStatus.BadRequest, "Content-Length header has an invalid value")

            length = declared

            contentLength.apply(reader, declared)

            return contentLength.also { _framing = it }
        }

        val transferEncoding = headers.getEntry(KnownHeaders.TransferEncoding)

        if (transferEncoding != null && transferEncoding.bytes.mismatch(ChunkedValue) == -1) {
            chunked.apply(reader)

            return chunked.also { _framing = it }
        }

        throw ProviderException(ResponseStatus.NotImplemented, "Request body has neither Content-Length nor Transfer-Encoding: chunked")
    }

    // endregion

    // region Functionality

    override fun asStream(): InputStream = stream ?: BodyStream(framing).also { stream = it }

    override suspend fun asMemory(): ByteBuffer {
        val existing = memory

        if (existing != null) {
            return existing.duplicate()
        }

        val read = framing.readToEnd()

        memory = read

        return read.duplicate()
    }

    // endregion

    private companion object {

        val ChunkedValue: ByteBuffer = ByteBuffer.wrap("chunked".toByteArray(Charsets.US_ASCII)).asReadOnlyBuffer()

    }

}
