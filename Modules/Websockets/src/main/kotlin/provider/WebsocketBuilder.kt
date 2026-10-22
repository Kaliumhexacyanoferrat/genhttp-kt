package org.genhttp.modules.websockets.provider

import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.BufferSize
import org.genhttp.modules.conversion.Formatting
import org.genhttp.modules.conversion.formatters.FormatterRegistry
import org.genhttp.modules.conversion.serializers.SerializationFormat
import org.genhttp.modules.conversion.serializers.json.JsonFormat

/**
 * Base class of the builders creating websocket handlers.
 *
 * @param T The type of the concrete builder
 */
public abstract class WebsocketBuilder<T : WebsocketBuilder<T>> : HandlerBuilder.WithConcerns<T> {

    /**
     * The concerns that will wrap the handler.
     */
    protected val concerns: MutableList<ConcernBuilder> = mutableListOf()

    /**
     * Whether the API user is expected to handle continuation frames manually.
     */
    protected var handleContinuationFramesManually: Boolean = false

    /**
     * Whether the data of a frame will be allocated so it can still be accessed after the next frame has been read.
     */
    protected var allocateFrameData: Boolean = true

    /**
     * The maximum size of the receive buffer.
     */
    protected var maxRxBufferSize: Int = BufferSize.Read

    /**
     * The formatters used to send and receive formatted data.
     */
    protected var formatters: FormatterRegistry = Formatting.default().build()

    /**
     * The format used to send and receive structured data.
     */
    protected var serializationFormat: SerializationFormat = JsonFormat()

    @Suppress("UNCHECKED_CAST")
    override fun add(concern: ConcernBuilder): T {
        concerns.add(concern)
        return this as T
    }

    /**
     * Sets the formatting registry used to send and receive formatted data.
     *
     * @param formatters The formatters to use for this socket
     */
    @Suppress("UNCHECKED_CAST")
    public fun formatters(formatters: FormatterRegistry): T {
        this.formatters = formatters
        return this as T
    }

    /**
     * Sets the serialization handler to send and receive structured data.
     *
     * @param format The format to use for this socket
     */
    @Suppress("UNCHECKED_CAST")
    public fun serialization(format: SerializationFormat): T {
        serializationFormat = format
        return this as T
    }

    /**
     * By default, messages split into multiple web socket frames will be transparently
     * handled and aggregated by the frame readers. If you would like to handle
     * such frames manually, you can set this option.
     */
    @Suppress("UNCHECKED_CAST")
    public fun handleContinuationFramesManually(): T {
        handleContinuationFramesManually = true
        return this as T
    }

    /**
     * By default, the handlers will allocate the data within a websocket frame, so the
     * data can still be referenced after the next message has been read from the connection.
     *
     * For high performance scenarios, you can disable the automatic allocation. In this case
     * you need to ensure, that access to frame data only happens while the frame is still the
     * latest read from the connection.
     */
    @Suppress("UNCHECKED_CAST")
    public fun doNotAllocateFrameData(): T {
        allocateFrameData = false
        return this as T
    }

    /**
     * Creates the settings for a new connection from the configuration of this builder.
     */
    protected fun buildSettings(): ConnectionSettings =
        ConnectionSettings(formatters, serializationFormat, handleContinuationFramesManually, allocateFrameData)

    abstract override fun build(): Handler

}
