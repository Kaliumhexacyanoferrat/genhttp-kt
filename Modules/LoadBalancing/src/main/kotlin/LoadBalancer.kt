package org.genhttp.modules.loadbalancing

import org.genhttp.modules.loadbalancing.provider.LoadBalancerBuilder

/**
 * Entry point to create handlers that distribute requests to a set of nodes.
 */
public object LoadBalancer {

    /**
     * Creates a new load balancer that can be configured with nodes.
     *
     * @return The newly created builder
     */
    public fun create(): LoadBalancerBuilder = LoadBalancerBuilder()

}
