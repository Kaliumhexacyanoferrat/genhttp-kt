package org.genhttp.modules.websockets.protocol

import org.genhttp.modules.websockets.RawFrameData
import org.genhttp.modules.websockets.SocketConnection
import org.genhttp.modules.websockets.WebsocketFrame

import java.nio.ByteBuffer

/**
 * A single frame read from the websocket connection.
 */
internal class ParsedWebsocketFrame private constructor(
    override val connection: SocketConnection,
    private var buffer: ByteBuffer,
    override val type: FrameType,
    override val fin: Boolean,
    private val frameError: FrameError?
) : WebsocketFrame, RawFrameData {

    private var cachedData: ByteBuffer? = null

    // region Get-/Setters

    override val raw: RawFrameData
        get() = this

    override var isSegmented: Boolean = false
        internal set

    /**
     * The segments of a frame that is assembled from multiple frames.
     */
    internal var segmentList: MutableList<ByteBuffer?>? = null

    override val segments: List<ByteBuffer?>?
        get() = segmentList

    override var memory: ByteBuffer
        get() = buffer.duplicate()
        internal set(value) {
            buffer = value
        }

    override val data: ByteBuffer
        get() = (cachedData ?: copyMemory().also { cachedData = it }).duplicate()

    // endregion

    // region Initialization

    internal constructor(connection: SocketConnection, rawData: ByteBuffer, type: FrameType, fin: Boolean = true) :
            this(connection, rawData, type, fin, null)

    internal constructor(connection: SocketConnection, type: FrameType, fin: Boolean = true) :
            this(connection, Empty, type, fin, null)

    // constructor overload for error frames
    internal constructor(connection: SocketConnection, frameError: FrameError, type: FrameType = FrameType.Error, fin: Boolean = true) :
            this(connection, Empty, type, fin, frameError)

    // endregion

    // region Functionality

    override fun isError(): FrameError? = if (type == FrameType.Error && frameError != null) frameError else null

    /**
     * For very rare use cases when the frame scope is outside the valid span of the connection's
     * internal buffer, e.g. ping/pong frames amid segmented frames.
     */
    internal fun setCachedData() {
        cachedData = copyMemory()
    }

    private fun copyMemory(): ByteBuffer {
        val source = buffer.duplicate()
        val copy = ByteArray(source.remaining())

        source.get(copy)

        return ByteBuffer.wrap(copy).asReadOnlyBuffer()
    }

    // endregion

    private companion object {

        private val Empty: ByteBuffer = ByteBuffer.allocate(0).asReadOnlyBuffer()

    }

}
