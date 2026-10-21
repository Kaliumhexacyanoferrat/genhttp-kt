package org.genhttp.engine.shared.types.body

/**
 * A body stream that can read the remaining content from the connection.
 */
public interface DrainableStream {

    /**
     * Reads and discards the remaining content of the body.
     */
    public fun drain()

}
