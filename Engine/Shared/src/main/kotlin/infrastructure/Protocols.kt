package org.genhttp.engine.shared.infrastructure

import org.genhttp.api.infrastructure.HttpProtocols

/**
 * Validates the protocols requested for an endpoint.
 */
public object Protocols {

    /**
     * Returns the protocols that can actually be served by the given endpoint configuration.
     */
    public fun validate(config: EndPointConfiguration): HttpProtocols {
        var requested = config.protocols

        if (config.security == null) {
            requested -= HttpProtocols.Http3

            if (requested == HttpProtocols.None) {
                throw UnsupportedOperationException("H3 can only be served over secure endpoints")
            }
        } else if (requested == HttpProtocols.None) {
            throw UnsupportedOperationException("At least one protocol has to be specified")
        }

        return requested
    }

}
