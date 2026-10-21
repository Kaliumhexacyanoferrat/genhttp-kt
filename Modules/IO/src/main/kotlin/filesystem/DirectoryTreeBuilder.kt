package org.genhttp.modules.io.filesystem

import org.genhttp.api.content.io.ResourceTree
import org.genhttp.api.infrastructure.Builder
import org.genhttp.api.infrastructure.BuilderMissingPropertyException

import java.io.FileNotFoundException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Builds a resource tree that provides the content of a directory.
 */
public class DirectoryTreeBuilder : Builder<ResourceTree> {

    private var directory: Path? = null

    /**
     * Sets the directory to be provided by the tree.
     *
     * @param directory The directory to be provided
     */
    public fun directory(directory: Path): DirectoryTreeBuilder {
        this.directory = directory
        return this
    }

    override fun build(): ResourceTree {
        val directory = directory ?: throw BuilderMissingPropertyException("directory")

        if (!Files.isDirectory(directory)) {
            throw FileNotFoundException("Directory '${directory.toAbsolutePath()}' does not exist")
        }

        return DirectoryTree(directory)
    }

}
