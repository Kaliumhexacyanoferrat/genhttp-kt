package org.genhttp.modules.io.filesystem

import org.genhttp.api.content.io.ResourceTree

import java.nio.file.Path

internal class DirectoryTree(directory: Path) : DirectoryContainer(directory), ResourceTree
