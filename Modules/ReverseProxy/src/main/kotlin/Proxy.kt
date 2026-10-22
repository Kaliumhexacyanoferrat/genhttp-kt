package org.genhttp.modules.reverseproxy

import org.genhttp.modules.reverseproxy.provider.ReverseProxyBuilder

/**
 * Entry point to create handlers that pass incoming requests to another server.
 */
public object Proxy {

    /**
     * Creates a handler that will route incoming requests to
     * the specified upstream server.
     *
     * @return The newly created handler
     */
    public fun create(): ReverseProxyBuilder = ReverseProxyBuilder()

}
