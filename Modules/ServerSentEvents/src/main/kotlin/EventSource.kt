package org.genhttp.modules.serversentevents

import org.genhttp.modules.serversentevents.handler.EventSourceHandlerBuilder

/**
 * Entry point to create handlers that push events to the connected clients (server sent events).
 */
public object EventSource {

    /**
     * Creates a new handler that keeps the connection to the client open to send events.
     *
     * @return The newly created event source handler builder
     */
    public fun create(): EventSourceHandlerBuilder = EventSourceHandlerBuilder()

}
