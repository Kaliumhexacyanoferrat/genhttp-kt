package org.genhttp.modules.directorybrowsing.provider

import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Concerns
import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.content.io.ResourceTree
import org.genhttp.api.infrastructure.Builder
import org.genhttp.api.infrastructure.BuilderMissingPropertyException

/**
 * Builds a handler that renders directory listings of a resource tree.
 */
public class ListingRouterBuilder : HandlerBuilder.WithConcerns<ListingRouterBuilder> {

    private val concerns = mutableListOf<ConcernBuilder>()

    private var tree: ResourceTree? = null

    // region Functionality

    /**
     * Sets the tree to be listed.
     *
     * @param tree The tree to be listed
     */
    public fun tree(tree: ResourceTree): ListingRouterBuilder {
        this.tree = tree
        return this
    }

    /**
     * Sets the tree to be listed.
     *
     * @param tree The tree to be listed
     */
    public fun tree(tree: Builder<ResourceTree>): ListingRouterBuilder = tree(tree.build())

    override fun add(concern: ConcernBuilder): ListingRouterBuilder {
        concerns.add(concern)
        return this
    }

    override fun build(): Handler {
        val tree = tree ?: throw BuilderMissingPropertyException("tree")

        return Concerns.chain(concerns, ListingRouter(tree))
    }

    // endregion

}
