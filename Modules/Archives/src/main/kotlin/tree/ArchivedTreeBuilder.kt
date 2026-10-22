package org.genhttp.modules.archives.tree

import org.genhttp.api.content.io.ResourceTree
import org.genhttp.api.infrastructure.Builder
import org.genhttp.modules.io.tracking.ChangeTrackingResource

/**
 * Builds a resource tree that expands an archive.
 *
 * @param source The resource granting access to the archive
 */
public class ArchivedTreeBuilder(private val source: ChangeTrackingResource) : Builder<ResourceTree> {

    override fun build(): ResourceTree = ArchivedTree(source)

}
