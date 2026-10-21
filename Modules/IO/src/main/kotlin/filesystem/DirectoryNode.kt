package org.genhttp.modules.io.filesystem

import org.genhttp.api.content.io.ResourceContainer
import org.genhttp.api.content.io.ResourceNode

import java.nio.file.Path

internal class DirectoryNode(directory: Path, override val parent: ResourceContainer) : DirectoryContainer(directory), ResourceNode {

    override val name: String
        get() = directory.fileName?.toString() ?: directory.toString()

}
