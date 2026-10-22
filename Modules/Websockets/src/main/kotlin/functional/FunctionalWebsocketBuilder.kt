package org.genhttp.modules.websockets.functional

import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.modules.conversion.formatters.FormatterRegistry
import org.genhttp.modules.conversion.serializers.SerializationFormat
import org.genhttp.modules.websockets.ReactiveConnection
import org.genhttp.modules.websockets.Websocket
import org.genhttp.modules.websockets.WebsocketFrame
import org.genhttp.modules.websockets.protocol.FrameError

/**
 * Builds a websocket handler that reacts to events by invoking the functions passed to the builder.
 */
public class FunctionalWebsocketBuilder : HandlerBuilder.WithConcerns<FunctionalWebsocketBuilder> {

    private val builder = Websocket.reactive()

    private var onConnected: suspend (ReactiveConnection) -> Unit = {}

    private var onMessage: suspend (ReactiveConnection, WebsocketFrame) -> Unit = { _, _ -> }
    private var onBinary: suspend (ReactiveConnection, WebsocketFrame) -> Unit = { _, _ -> }
    private var onContinue: suspend (ReactiveConnection, WebsocketFrame) -> Unit = { _, _ -> }
    private var onPing: suspend (ReactiveConnection, WebsocketFrame) -> Unit = { c, m -> c.pong(m.data) }
    private var onPong: suspend (ReactiveConnection, WebsocketFrame) -> Unit = { _, _ -> }
    private var onClose: suspend (ReactiveConnection, WebsocketFrame) -> Unit = { c, _ -> c.close() }

    private var onError: suspend (ReactiveConnection, FrameError) -> Boolean = { _, _ -> true }

    override fun add(concern: ConcernBuilder): FunctionalWebsocketBuilder {
        builder.add(concern)
        return this
    }

    /**
     * Sets the formatting registry used to send and receive formatted data.
     *
     * @param formatters The formatters to use for this socket
     */
    public fun formatters(formatters: FormatterRegistry): FunctionalWebsocketBuilder {
        builder.formatters(formatters)
        return this
    }

    /**
     * Sets the serialization handler to send and receive structured data.
     *
     * @param format The format to use for this socket
     */
    public fun serialization(format: SerializationFormat): FunctionalWebsocketBuilder {
        builder.serialization(format)
        return this
    }

    /**
     * By default, messages split into multiple web socket frames will be transparently
     * handled and aggregated by the frame readers. If you would like to handle
     * such frames manually, you can set this option.
     */
    public fun handleContinuationFramesManually(): FunctionalWebsocketBuilder {
        builder.handleContinuationFramesManually()
        return this
    }

    /**
     * By default, the handlers will allocate the data within a websocket frame, so the
     * data can still be referenced after the next message has been read from the connection.
     *
     * For high performance scenarios, you can disable the automatic allocation. In this case
     * you need to ensure, that access to frame data only happens while the frame is still the
     * latest read from the connection.
     */
    public fun doNotAllocateFrameData(): FunctionalWebsocketBuilder {
        builder.doNotAllocateFrameData()
        return this
    }

    /**
     * Invoked when a new client has connected.
     *
     * The function receives the connection to the client.
     *
     * @param handler The function to be invoked
     */
    public fun onConnected(handler: suspend (ReactiveConnection) -> Unit): FunctionalWebsocketBuilder {
        onConnected = handler
        return this
    }

    /**
     * Invoked when a message frame has been received.
     *
     * The function receives the connection to the client and the incoming message frame.
     *
     * @param handler The function to be invoked
     */
    public fun onMessage(handler: suspend (ReactiveConnection, WebsocketFrame) -> Unit): FunctionalWebsocketBuilder {
        onMessage = handler
        return this
    }

    /**
     * Invoked when a binary message frame has been received.
     *
     * The function receives the connection to the client and the incoming binary message frame.
     *
     * @param handler The function to be invoked
     */
    public fun onBinary(handler: suspend (ReactiveConnection, WebsocketFrame) -> Unit): FunctionalWebsocketBuilder {
        onBinary = handler
        return this
    }

    /**
     * Invoked when the client splits frames into multiple messages
     * and an additional frame after the first has been received.
     *
     * If fragmentation occurs, you will receive a message frame with
     * fin set to false. After this message, you will receive additional
     * messages until one finally has fin set to true. From those frames,
     * you have to construct the overall message originally sent by the client.
     *
     * The function receives the connection to the client and the incoming message frame.
     *
     * @param handler The function to be invoked
     */
    public fun onContinue(handler: suspend (ReactiveConnection, WebsocketFrame) -> Unit): FunctionalWebsocketBuilder {
        onContinue = handler
        return this
    }

    /**
     * Invoked when a ping frame has been received.
     *
     * The function receives the connection to the client and the incoming ping frame.
     *
     * @param handler The function to be invoked
     */
    public fun onPing(handler: suspend (ReactiveConnection, WebsocketFrame) -> Unit): FunctionalWebsocketBuilder {
        onPing = handler
        return this
    }

    /**
     * Invoked when a pong frame has been received.
     *
     * The function receives the connection to the client and the incoming pong frame.
     *
     * @param handler The function to be invoked
     */
    public fun onPong(handler: suspend (ReactiveConnection, WebsocketFrame) -> Unit): FunctionalWebsocketBuilder {
        onPong = handler
        return this
    }

    /**
     * Invoked when the client requests to close the connection.
     *
     * The function receives the connection to the client and the frame sent by the client.
     *
     * @param handler The function to be invoked
     */
    public fun onClose(handler: suspend (ReactiveConnection, WebsocketFrame) -> Unit): FunctionalWebsocketBuilder {
        onClose = handler
        return this
    }

    /**
     * Invoked if the server failed to read a frame from the connection.
     *
     * The function receives the connection to the client and the error which ocurred and
     * returns true, if the connection should be closed.
     *
     * @param handler The function to be invoked
     */
    public fun onError(handler: suspend (ReactiveConnection, FrameError) -> Boolean): FunctionalWebsocketBuilder {
        onError = handler
        return this
    }

    override fun build(): Handler {
        val handler = FunctionalHandler(onConnected, onMessage, onBinary, onContinue, onPing, onPong, onClose, onError)

        builder.handler(handler)

        return builder.build()
    }

}
