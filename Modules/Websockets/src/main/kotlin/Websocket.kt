package org.genhttp.modules.websockets

import org.genhttp.modules.websockets.functional.FunctionalWebsocketBuilder
import org.genhttp.modules.websockets.imperative.ImperativeWebsocketBuilder
import org.genhttp.modules.websockets.reactive.ReactiveWebsocketBuilder

/**
 * Provides handlers that allow you to process a web socket connection.
 * There are several flavors available, depending on your personal
 * preference and the nature of the problem you would like to solve.
 */
public object Websocket {

    /**
     * Creates a web socket that allows you to read
     * messages one-by-one from the socket connection and handle
     * them as needed, typically in a loop.
     */
    public fun imperative(): ImperativeWebsocketBuilder = ImperativeWebsocketBuilder()

    /**
     * Creates a web socket that allows you to specify a handler
     * implementation that reacts to specific events, such as
     * a new message being received.
     *
     * In contrast to the imperative web socket, this socket will
     * provide a message pump that will read incoming messages
     * and dispatch them to your handler.
     */
    public fun reactive(): ReactiveWebsocketBuilder = ReactiveWebsocketBuilder()

    /**
     * Creates a web socket that allows you to specify functions
     * for the type of messages you would like to handle.
     *
     * In contrast to the reactive web socket, this builder allows
     * you to directly handle events without the need of implementing
     * a handler interface.
     */
    public fun functional(): FunctionalWebsocketBuilder = FunctionalWebsocketBuilder()

}
