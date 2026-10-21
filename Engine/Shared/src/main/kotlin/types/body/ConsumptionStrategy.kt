package org.genhttp.engine.shared.types.body

/**
 * The way a request body is consumed by the handler.
 */
internal enum class ConsumptionStrategy {

    Stream,

    Memory

}

/**
 * A body stream that can read the remaining content from the connection.
 */
public interface DrainableStream {

    /**
     * Reads and discards the remaining content of the body.
     */
    public fun drain()

}
