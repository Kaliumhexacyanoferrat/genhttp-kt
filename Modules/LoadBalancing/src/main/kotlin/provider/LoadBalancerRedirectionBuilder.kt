package org.genhttp.modules.loadbalancing.provider

import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Concerns
import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.BuilderMissingPropertyException

/**
 * Builds a handler that redirects the client to another location.
 */
public class LoadBalancerRedirectionBuilder : HandlerBuilder.WithConcerns<LoadBalancerRedirectionBuilder> {

    private val concerns = mutableListOf<ConcernBuilder>()

    private var root: String? = null

    // region Functionality

    override fun add(concern: ConcernBuilder): LoadBalancerRedirectionBuilder {
        concerns.add(concern)
        return this
    }

    /**
     * Sets the root location the client will be redirected to.
     *
     * @param node The root location to redirect the client to
     */
    public fun root(node: String): LoadBalancerRedirectionBuilder {
        root = node
        return this
    }

    override fun build(): Handler {
        val root = root ?: throw BuilderMissingPropertyException("root")

        return Concerns.chain(concerns, LoadBalancerRedirectionHandler(root))
    }

    // endregion

}
