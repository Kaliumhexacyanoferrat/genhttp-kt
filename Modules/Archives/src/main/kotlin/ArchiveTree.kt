package org.genhttp.modules.archives

import org.genhttp.api.content.io.Resource
import org.genhttp.api.content.io.ResourceBuilder
import org.genhttp.modules.archives.tree.ArchivedTreeBuilder
import org.genhttp.modules.io.buildWithTracking
import org.genhttp.modules.io.tracking.ChangeTrackingResource

/**
 * Provides archives (such as ZIP, TAR or 7z files) as resource trees.
 *
 * Uses Apache Commons Compress to open and evaluate archives. Archive parsing is done once on first request.
 * Uses change tracking to determine changes to the underlying archive and reparses again if needed. Files are
 * always read from a fresh stream directly from the archive.
 */
public object ArchiveTree {

    /**
     * Create a resource tree from the given archive resource.
     *
     * @param source The resource granting access to the archive
     */
    public fun <T : ResourceBuilder<T>> from(source: ResourceBuilder<T>): ArchivedTreeBuilder = from(source.buildWithTracking())

    /**
     * Create a resource tree from the given archive resource.
     *
     * @param source The resource granting access to the archive
     */
    public fun from(source: Resource): ArchivedTreeBuilder = from(ChangeTrackingResource(source))

    /**
     * Create a resource tree from the given archive resource.
     *
     * @param source The resource granting access to the archive
     */
    public fun from(source: ChangeTrackingResource): ArchivedTreeBuilder = ArchivedTreeBuilder(source)

}
