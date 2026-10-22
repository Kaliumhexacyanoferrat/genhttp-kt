package org.genhttp.modules.staticwebsites.provider

import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Concerns
import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.content.io.ResourceTree
import org.genhttp.api.infrastructure.BuilderMissingPropertyException

/**
 * Builds a handler that serves a static website.
 */
public class StaticWebsiteBuilder : HandlerBuilder.WithConcerns<StaticWebsiteBuilder> {

    private val concerns = mutableListOf<ConcernBuilder>()

    private var tree: ResourceTree? = null

    // region Functionality

    /**
     * Sets the tree that provides the files of the website.
     *
     * @param tree The tree to serve the website from
     */
    public fun tree(tree: ResourceTree): StaticWebsiteBuilder {
        this.tree = tree
        return this
    }

    override fun add(concern: ConcernBuilder): StaticWebsiteBuilder {
        concerns.add(concern)
        return this
    }

    override fun build(): Handler {
        val tree = tree ?: throw BuilderMissingPropertyException("tree")

        return Concerns.chain(concerns, StaticWebsiteHandler(tree))
    }

    // endregion

}
