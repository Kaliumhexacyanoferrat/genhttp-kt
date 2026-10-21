package org.genhttp.engine.internal

import org.genhttp.api.infrastructure.ServerHost
import org.genhttp.engine.internal.infrastructure.ThreadedServerHost

/**
 * Allows to create server instances running on the internal engine.
 */
public object Host {

    /**
     * Creates a new server host that can be used to configure and run a server instance.
     */
    public fun create(): ServerHost = ThreadedServerHost()

}
