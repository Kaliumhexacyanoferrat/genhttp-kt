package org.genhttp.modules.singlepageapplications.provider

import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Concerns
import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.content.io.ResourceTree
import org.genhttp.api.infrastructure.Builder
import org.genhttp.api.infrastructure.BuilderMissingPropertyException

/**
 * Builds a handler that serves a single page application.
 */
public class SinglePageBuilder : HandlerBuilder.WithConcerns<SinglePageBuilder> {

    private val concerns = mutableListOf<ConcernBuilder>()

    private var serverSideRouting: Boolean = false

    private var tree: ResourceTree? = null

    // region Functionality

    override fun add(concern: ConcernBuilder): SinglePageBuilder {
        concerns.add(concern)
        return this
    }

    /**
     * Sets the tree that provides the files of the application.
     *
     * @param tree The tree to serve the application from
     */
    public fun tree(tree: ResourceTree): SinglePageBuilder {
        this.tree = tree
        return this
    }

    /**
     * Sets the tree that provides the files of the application.
     *
     * @param tree The tree to serve the application from
     */
    public fun tree(tree: Builder<ResourceTree>): SinglePageBuilder = tree(tree.build())

    /**
     * If enabled, the server will serve the SPA index for
     * every unknown route, which enables path based routing.
     */
    public fun serverSideRouting(): SinglePageBuilder {
        serverSideRouting = true
        return this
    }

    override fun build(): Handler {
        val tree = tree ?: throw BuilderMissingPropertyException("tree")

        return Concerns.chain(concerns, SinglePageProvider(tree, serverSideRouting))
    }

    // endregion

}
