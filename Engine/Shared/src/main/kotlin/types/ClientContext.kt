package org.genhttp.engine.shared.types

import org.genhttp.api.infrastructure.Server
import org.genhttp.engine.shared.io.ConnectionWriter

/**
 * The state of a client connection required to write responses (`IClientContext` in GenHTTP).
 */
public interface ClientContext {

    /**
     * The server the connection has been accepted by.
     */
    public val server: Server

    /**
     * The buffered writer of the connection.
     */
    public val writer: ConnectionWriter

}
