package org.genhttp.modules.singlepageapplications

import org.genhttp.api.content.io.ResourceTree
import org.genhttp.api.infrastructure.Builder
import org.genhttp.modules.singlepageapplications.provider.SinglePageBuilder

/**
 * Entry point to create handlers that serve single page applications.
 */
public object SinglePageApplication {

    /**
     * Creates a single page application from the given resource tree.
     *
     * @param tree The tree to serve the application from
     */
    public fun from(tree: Builder<ResourceTree>): SinglePageBuilder = from(tree.build())

    /**
     * Creates a single page application from the given resource tree.
     *
     * @param tree The tree to serve the application from
     */
    public fun from(tree: ResourceTree): SinglePageBuilder = SinglePageBuilder().tree(tree)

}
