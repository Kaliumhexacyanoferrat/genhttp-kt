package org.genhttp.modules.loadbalancing.provider

import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Concerns
import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.Priority
import org.genhttp.modules.loadbalancing.PriorityEvaluation
import org.genhttp.modules.reverseproxy.Proxy

/**
 * Builds a handler that distributes requests to a set of nodes.
 */
public class LoadBalancerBuilder : HandlerBuilder.WithConcerns<LoadBalancerBuilder> {

    private val concerns = mutableListOf<ConcernBuilder>()

    private val nodes = mutableListOf<Pair<HandlerBuilder, PriorityEvaluation>>()

    // region Functionality

    override fun add(concern: ConcernBuilder): LoadBalancerBuilder {
        concerns.add(concern)
        return this
    }

    /**
     * Adds a node to the load balancer.
     *
     * @param handler The handler responsible to handle the requests passed to this node
     * @param priority Determines the priority of the node for a request (defaults to medium priority)
     */
    public fun add(handler: HandlerBuilder, priority: PriorityEvaluation? = null): LoadBalancerBuilder {
        nodes.add(Pair(handler, priority ?: DefaultPriority))
        return this
    }

    /**
     * Adds a node to the load balancer that redirects the client to the given location.
     *
     * @param node The root location to redirect the client to
     * @param priority Determines the priority of the node for a request (defaults to medium priority)
     */
    public fun redirect(node: String, priority: PriorityEvaluation? = null): LoadBalancerBuilder =
        add(LoadBalancerRedirectionBuilder().root(node), priority)

    /**
     * Adds a node to the load balancer that proxies the request to the given upstream server.
     *
     * @param node The upstream server to proxy the request to
     * @param priority Determines the priority of the node for a request (defaults to medium priority)
     */
    public fun proxy(node: String, priority: PriorityEvaluation? = null): LoadBalancerBuilder =
        add(Proxy.create().upstream(node), priority)

    override fun build(): Handler = Concerns.chain(concerns, LoadBalancerHandler(nodes))

    // endregion

    private companion object {

        val DefaultPriority: PriorityEvaluation = { Priority.Medium }

    }

}
