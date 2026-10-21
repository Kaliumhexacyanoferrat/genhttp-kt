package org.genhttp.modules.io.filesystem

import org.genhttp.api.content.io.Resource
import org.genhttp.api.content.io.ResourceContainer
import org.genhttp.api.content.io.ResourceNode
import org.genhttp.api.protocol.PathSegment
import org.genhttp.modules.io.fromFile

import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.time.Instant

internal open class DirectoryContainer(protected val directory: Path) : ResourceContainer {

    override val modified: Instant?
        get() = try {
            Files.getLastModifiedTime(directory).toInstant()
        } catch (_: java.io.IOException) {
            null
        }

    override suspend fun getNodes(): Collection<ResourceNode> {
        val result = mutableListOf<ResourceNode>()

        Files.newDirectoryStream(directory).use { entries ->
            for (entry in entries) {
                if (Files.isDirectory(entry)) {
                    result.add(DirectoryNode(entry, this))
                }
            }
        }

        return result
    }

    override suspend fun getResources(): Collection<Resource> {
        val result = mutableListOf<Resource>()

        Files.newDirectoryStream(directory).use { entries ->
            for (entry in entries) {
                if (Files.isRegularFile(entry)) {
                    result.add(Resource.fromFile(entry).build())
                }
            }
        }

        return result
    }

    override suspend fun tryGetNode(segment: PathSegment): ResourceNode? {
        val path = resolve(segment) ?: return null

        if (Files.isDirectory(path)) {
            return DirectoryNode(path, this)
        }

        return null
    }

    override suspend fun tryGetResource(segment: PathSegment): Resource? {
        val path = resolve(segment) ?: return null

        if (Files.isRegularFile(path)) {
            return Resource.fromFile(path).build()
        }

        return null
    }

    /**
     * Resolves the segment within the directory, ensuring that the result cannot leave it.
     */
    private fun resolve(segment: PathSegment): Path? {
        try {
            val root = directory.toAbsolutePath().normalize()

            val resolved = root.resolve(segment.decode()).normalize()

            return if (resolved.startsWith(root)) resolved else null
        } catch (_: InvalidPathException) {
            return null
        }
    }

}
