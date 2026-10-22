package org.genhttp.modules.websockets

/**
 * A handler that can be passed to the imperative web socket
 * implementation to implement a message pump.
 */
public interface ImperativeHandler {

    /**
     * Invoked by the handler as soon as a connection to
     * a web socket client is established. Use the connection
     * to read incoming messages and handle them as needed.
     *
     * @param connection The connection to read incoming web socket frames from
     */
    public suspend fun handle(connection: ImperativeConnection)

}
