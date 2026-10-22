package org.genhttp.modules.archives.tree

import org.genhttp.api.content.io.Resource
import org.genhttp.api.content.io.ResourceContainer
import org.genhttp.api.content.io.ResourceNode
import org.genhttp.api.protocol.PathSegment

import java.io.InputStream
import java.time.Instant

/**
 * Creates the stream of an archive entry from a fresh stream of the archive and the key of the entry.
 */
internal typealias EntryStreamFactory = (InputStream, String) -> InputStream

/**
 * A directory within an archive.
 *
 * @param nodeName The name of the node (null for the root node)
 * @param parentContainer The parent of the node (null for the root node)
 */
public class ArchiveNode(private val nodeName: String?, private val parentContainer: ResourceContainer?) : ResourceNode {

    private val children = LinkedHashMap<PathSegment, ArchiveNode>()

    private val resources = LinkedHashMap<PathSegment, ArchiveResource>()

    // region Get-/Setters

    override val name: String
        get() = nodeName ?: throw IllegalStateException("Root node does not have a name")

    override val parent: ResourceContainer
        get() = parentContainer ?: throw IllegalStateException("Root node does not have a parent node")

    override var modified: Instant? = null
        private set

    // endregion

    // region Functionality

    override suspend fun tryGetNode(segment: PathSegment): ResourceNode? = children[segment]

    override suspend fun getNodes(): Collection<ResourceNode> = children.values

    override suspend fun tryGetResource(segment: PathSegment): Resource? = resources[segment]

    override suspend fun getResources(): Collection<Resource> = resources.values

    internal fun getOrCreate(child: String): ArchiveNode {
        val segment = PathSegment(child)

        val found = children[segment]

        if (found != null) {
            return found
        }

        val created = ArchiveNode(child, this)

        children[segment] = created

        return created
    }

    internal fun addFile(name: String, entry: ArchiveEntryInfo, archive: Resource, streamFactory: EntryStreamFactory) {
        val segment = PathSegment(name)

        if (resources.containsKey(segment)) {
            throw IllegalArgumentException("An item with the same key has already been added: '$name'")
        }

        resources[segment] = ArchiveResource(archive, entry, name, streamFactory)
    }

    internal fun adapt(entry: ArchiveEntryInfo) {
        modified = entry.modified
    }

    // endregion

}
