package org.genhttp.engine.shared.types

import org.genhttp.api.content.ProviderException
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.HttpProtocol
import org.genhttp.api.protocol.KnownHeaders
import org.genhttp.api.protocol.RequestBody
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.engine.shared.io.ConnectionReader
import org.genhttp.engine.shared.io.ConnectionWriter
import org.genhttp.engine.shared.types.body.BodyFraming
import org.genhttp.engine.shared.types.body.BodyState
import org.genhttp.engine.shared.types.body.BodyStream
import org.genhttp.engine.shared.types.body.ChunkedFraming
import org.genhttp.engine.shared.types.body.ContentLengthFraming
import org.genhttp.engine.shared.types.body.ContinueReader

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

    private val continueReader = ContinueReader()

    private var _reader: ConnectionReader? = null

    private var continueWriter: ConnectionWriter? = null

    private var state = BodyState.Pending

    private var present = false

    private var contentLengthIndex = -1

    private var transferEncodingIndex = -1

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

    /**
     * @param writer The writer of the connection, used to send interim responses (if supported)
     */
    internal fun apply(reader: ConnectionReader, writer: ConnectionWriter?) {
        _reader = reader
        state = BodyState.Pending

        wrapper = null
        wrapped = null

        val expectation = scanHeaders()

        present = contentLengthIndex >= 0 || transferEncodingIndex >= 0

        if (present && writer != null && expectation >= 0 && expectsContinue(expectation)) {
            continueWriter = writer
            continueReader.arm(writer)
        }
    }

    /**
     * Finds the headers that describe the body in a single pass.
     *
     * @return The index of the Expect header, or -1 if there is none
     */
    private fun scanHeaders(): Int {
        val headers = header.headers

        var contentLength = -1
        var transferEncoding = -1
        var expectation = -1

        for (i in 0 until headers.count) {
            val key = headers.getMemoryEntry(i).first

            if (contentLength < 0 && matches(KnownHeaders.ContentLength, key)) {
                contentLength = i
            } else if (transferEncoding < 0 && matches(KnownHeaders.TransferEncoding, key)) {
                transferEncoding = i
            } else if (expectation < 0 && matches(KnownHeaders.Expect, key)) {
                expectation = i
            }
        }

        contentLengthIndex = contentLength
        transferEncodingIndex = transferEncoding

        return expectation
    }

    private fun matches(name: ByteString, key: ByteBuffer): Boolean = key.remaining() == name.length && name.contentEquals(key)

    /**
     * Checks whether the client waits for a 100 (Continue) before sending the body. The expectation is ignored for
     * HTTP/1.0 clients (RFC 9110, section 10.1.1).
     */
    private fun expectsContinue(index: Int): Boolean =
        header.protocol != HttpProtocol.Http10 && ContinueReader.isContinue(headerValue(index))

    private fun headerValue(index: Int): ByteString = ByteString(header.headers.getMemoryEntry(index).second)

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

        contentLengthIndex = -1
        transferEncodingIndex = -1

        _framing?.let {
            it.reset()
            _framing = null
        }

        length = null

        stream = null
        memory = null

        wrapper = null
        wrapped = null

        if (continueWriter != null) {
            continueReader.reset()
            continueWriter = null
        }
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
     * Invoked right before the final response is written to the connection. From here on, reading the body no
     * longer sends a 100 (Continue), as no interim response may follow the final one.
     */
    internal fun responseStarting() {
        if (continueWriter != null) {
            continueReader.disarm()
        }
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

        val writer = continueWriter

        if (writer != null) {
            return finishExpected(framing, writer)
        }

        return framing.skip()
    }

    /**
     * Skips the body of a client that expected a 100 (Continue). If the 100 has never been sent, the client holds its
     * body back until it hears from the server, so the final response needs to be on the wire before the client will
     * either send the body or close the connection.
     */
    private fun finishExpected(framing: BodyFraming, writer: ConnectionWriter): Boolean {
        continueReader.disarm()

        if (!continueReader.sent) {
            writer.flush()
        }

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
        val expectation = if (continueWriter != null) continueReader else null

        if (contentLengthIndex >= 0) {
            val declared = headerValue(contentLengthIndex).toString().toLongOrNull()?.takeIf { it >= 0 }
                ?: throw ProviderException(ResponseStatus.BadRequest, "Content-Length header has an invalid value")

            length = declared

            contentLength.apply(reader, declared, expectation)

            return contentLength.also { _framing = it }
        }

        if (transferEncodingIndex >= 0 && headerValue(transferEncodingIndex).bytes.mismatch(ChunkedValue) == -1) {
            chunked.apply(reader, expectation)

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
