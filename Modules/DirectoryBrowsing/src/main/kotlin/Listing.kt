package org.genhttp.modules.directorybrowsing

import org.genhttp.api.content.io.ResourceTree
import org.genhttp.api.infrastructure.Builder
import org.genhttp.modules.directorybrowsing.provider.ListingRouterBuilder

/**
 * Entry point to create handlers that render directory listings of resource trees.
 */
public object Listing {

    /**
     * Creates a handler that renders a listing of the given tree.
     *
     * @param tree The tree to be listed
     */
    public fun from(tree: Builder<ResourceTree>): ListingRouterBuilder = from(tree.build())

    /**
     * Creates a handler that renders a listing of the given tree.
     *
     * @param tree The tree to be listed
     */
    public fun from(tree: ResourceTree): ListingRouterBuilder = ListingRouterBuilder().tree(tree)

}
