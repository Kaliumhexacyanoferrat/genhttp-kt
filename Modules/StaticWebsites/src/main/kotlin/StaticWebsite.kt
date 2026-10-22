package org.genhttp.modules.staticwebsites

import org.genhttp.api.content.io.ResourceTree
import org.genhttp.api.infrastructure.Builder
import org.genhttp.modules.staticwebsites.provider.StaticWebsiteBuilder

/**
 * Entry point to create handlers that serve static websites.
 */
public object StaticWebsite {

    /**
     * Creates a static website from the given resource tree which
     * will automatically serve index files (index.html, index.htm)
     * and provide a sitemap and robots instruction file.
     *
     * @param tree The resource to generate the application from
     */
    public fun from(tree: Builder<ResourceTree>): StaticWebsiteBuilder = from(tree.build())

    /**
     * Creates a static website from the given resource tree which
     * will automatically serve index files (index.html, index.htm)
     * and provide a sitemap and robots instruction file.
     *
     * @param tree The resource to generate the application from
     */
    public fun from(tree: ResourceTree): StaticWebsiteBuilder = StaticWebsiteBuilder().tree(tree)

}
