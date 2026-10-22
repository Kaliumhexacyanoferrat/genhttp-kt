package org.genhttp.modules.virtualhosting

import org.genhttp.modules.virtualhosting.provider.VirtualHostRouterBuilder

/**
 * Entry point to create handlers that serve different content based on the `Host` header sent by the client.
 */
public object VirtualHosts {

    /**
     * Creates a new virtual host router.
     *
     * @return The newly created router builder
     */
    public fun create(): VirtualHostRouterBuilder = VirtualHostRouterBuilder()

}
