package org.genhttp.modules.files

import org.genhttp.api.content.io.ResourceTree
import org.genhttp.api.infrastructure.Builder
import org.genhttp.modules.files.multi.FileAssetsBuilder
import org.genhttp.modules.files.multi.TreeAssetsBuilder

import java.nio.file.Path

/**
 * Serves resources, either static or dynamically changing.
 */
public object Assets {

    /**
     * Creates a handler to hierarchical serve the given resource tree.
     *
     * @param tree The tree to be served
     * @return The newly created handler to serve the tree
     */
    public fun from(tree: Builder<ResourceTree>): TreeAssetsBuilder = TreeAssetsBuilder(tree.build())

    /**
     * Creates a handler to hierarchical serve the given resource tree.
     *
     * @param tree The tree to be served
     * @return The newly created handler to serve the tree
     */
    public fun from(tree: ResourceTree): TreeAssetsBuilder = TreeAssetsBuilder(tree)

    /**
     * Creates a handler to serve the given directory.
     *
     * Compared to the generic tree based version, this handler provides additional optimizations to
     * improve performance.
     *
     * @param directory The directory to be served
     * @return The newly created handler
     */
    public fun from(directory: String): FileAssetsBuilder = FileAssetsBuilder(Path.of(directory))

    /**
     * Creates a handler to serve the given directory.
     *
     * Compared to the generic tree based version, this handler provides additional optimizations to
     * improve performance.
     *
     * @param directory The directory to be served
     * @return The newly created handler
     */
    public fun from(directory: Path): FileAssetsBuilder = FileAssetsBuilder(directory)

}
