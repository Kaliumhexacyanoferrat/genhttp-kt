package org.genhttp.modules.io.classpath

import org.genhttp.api.content.io.Resource
import org.genhttp.api.content.io.ResourceContainer
import org.genhttp.api.content.io.ResourceNode
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.PathSegment
import org.genhttp.modules.io.guessContentType

import java.time.Instant

internal open class ClasspathResourceContainer(source: ClassLoader, prefix: String, content: ClasspathContent) : ResourceContainer {

    private val nodes = LinkedHashMap<PathSegment, ResourceNode>()

    private val resources = LinkedHashMap<PathSegment, Resource>()

    override val modified: Instant? = content.files.values.maxOfOrNull { it.modified } ?: Instant.now()

    init {
        val children = LinkedHashMap<String, ClasspathContent>()

        for ((relative, entry) in content.files) {
            val index = relative.indexOf('/')

            if (index < 0) {
                val type = relative.guessContentType() ?: ContentType.ApplicationForceDownload

                resources[PathSegment(relative)] = ClasspathResource(source, joinClasspathPath(prefix, relative), entry.url, relative, type, entry.modified)
            } else {
                children.getOrPut(relative.substring(0, index)) { ClasspathContent() }.files[relative.substring(index + 1)] = entry
            }
        }

        for (directory in content.directories) {
            val index = directory.indexOf('/')

            if (index < 0) {
                children.getOrPut(directory) { ClasspathContent() }
            } else {
                children.getOrPut(directory.substring(0, index)) { ClasspathContent() }.directories.add(directory.substring(index + 1))
            }
        }

        for ((name, child) in children) {
            nodes[PathSegment(name)] = ClasspathResourceNode(source, joinClasspathPath(prefix, name), child, this, name)
        }
    }

    override suspend fun getNodes(): Collection<ResourceNode> = nodes.values

    override suspend fun getResources(): Collection<Resource> = resources.values

    override suspend fun tryGetNode(segment: PathSegment): ResourceNode? = nodes[segment]

    override suspend fun tryGetResource(segment: PathSegment): Resource? = resources[segment]

}
