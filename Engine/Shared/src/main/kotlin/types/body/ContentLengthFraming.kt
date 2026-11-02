package org.genhttp.engine.shared.types.body

import org.genhttp.api.content.ProviderException
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.engine.shared.io.ConnectionReader

import java.nio.ByteBuffer

/**
 * A body framed by a `Content-Length` header.
 */
internal class ContentLengthFraming : BodyFraming {

    private var _reader: ConnectionReader? = null

    private var _length = 0L

    private var remaining = 0L

    // bytes handed out as a view into the buffer of the reader, consumed when the body is skipped
    private var held = 0

    // region Get-/Setters

    override val length: Long?
        get() = _length

    override val position: Long
        get() = _length - remaining

    private val reader: ConnectionReader
        get() = _reader ?: throw IllegalStateException("Framing has not been initialized")

    // endregion

    // region Initialization

    fun apply(reader: ConnectionReader, length: Long) {
        _reader = reader
        _length = length
        remaining = length
        held = 0
    }

    override fun reset() {
        _reader = null
        _length = 0
        remaining = 0
        held = 0
    }

    // endregion

    // region Functionality

    override fun read(target: ByteArray, offset: Int, length: Int): Int {
        if (remaining == 0L || length == 0) {
            return 0
        }

        val reader = reader

        if (!awaitData(reader)) {
            throw unexpectedEnd()
        }

        val count = minOf(length.toLong(), remaining, reader.available.toLong()).toInt()

        // copies buffered data only, as data is available
        reader.read(target, offset, count)

        remaining -= count

        return count
    }

    override fun readToEnd(): ByteBuffer {
        if (remaining == 0L) {
            return Empty
        }

        if (remaining > Int.MAX_VALUE) {
            throw ProviderException(ResponseStatus.RequestEntityTooLarge, "Request body is too large to be read into memory")
        }

        val reader = reader
        val size = remaining.toInt()

        if (!awaitData(reader)) {
            throw unexpectedEnd()
        }

        if (reader.available >= size) {
            // the view points into the buffer of the reader, so it is consumed when the body is skipped
            held = size
            remaining = 0

            return reader.data().slice(0, size).asReadOnlyBuffer()
        }

        // the body has not been received completely, so it is copied instead of growing the buffer of the reader
        val body = ByteArray(size)

        var position = 0

        while (position < size) {
            val read = reader.read(body, position, size - position)

            if (read < 0) {
                throw unexpectedEnd()
            }

            position += read
            remaining -= read
        }

        return ByteBuffer.wrap(body).asReadOnlyBuffer()
    }

    override fun skip(): Boolean {
        val reader = reader

        if (held > 0) {
            reader.consume(held)
            held = 0
        }

        while (remaining > 0) {
            if (!awaitData(reader)) {
                return false
            }

            val count = minOf(remaining, reader.available.toLong()).toInt()

            reader.consume(count)

            remaining -= count
        }

        return true
    }

    /**
     * Makes sure that data is buffered, waiting for it if necessary (`PipeReader.ReadAsync` upstream).
     *
     * @return false, if the input ended without any data being buffered
     */
    private fun awaitData(reader: ConnectionReader): Boolean = reader.available > 0 || reader.fill() >= 0

    private fun unexpectedEnd(): ProviderException = ProviderException(ResponseStatus.BadRequest, "Unexpected end of body")

    // endregion

    private companion object {

        val Empty: ByteBuffer = ByteBuffer.allocate(0).asReadOnlyBuffer()

    }

}
