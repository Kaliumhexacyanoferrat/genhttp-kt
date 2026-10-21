package org.genhttp.modules.io.virtualtrees

import org.genhttp.api.content.io.Resource
import org.genhttp.api.content.io.ResourceContainer
import org.genhttp.api.content.io.ResourceNode
import org.genhttp.api.content.io.ResourceTree
import org.genhttp.api.protocol.PathSegment

import java.time.Instant

/**
 * A resource tree that combines other trees, containers and resources.
 *
 * @param nodes The nodes of the tree, created with the tree as their parent
 * @param resources The resources provided by the tree
 */
public class VirtualTree(
    nodes: Map<PathSegment, (ResourceContainer) -> ResourceNode>,
    private val resources: Map<PathSegment, Resource>
) : ResourceTree {

    private val nodes: Map<PathSegment, ResourceNode>

    init {
        val built = LinkedHashMap<PathSegment, ResourceNode>(nodes.size)

        for ((key, factory) in nodes) {
            built[key] = factory(this)
        }

        this.nodes = built
    }

    override val modified: Instant?
        get() {
            val nodeDates = nodes.values.mapNotNull { it.modified }
            val resourceDates = resources.values.mapNotNull { it.modified }

            return (nodeDates + resourceDates).maxOrNull()
        }

    override suspend fun getNodes(): Collection<ResourceNode> = nodes.values

    override suspend fun getResources(): Collection<Resource> = resources.values

    override suspend fun tryGetNode(segment: PathSegment): ResourceNode? = nodes[segment]

    override suspend fun tryGetResource(segment: PathSegment): Resource? = resources[segment]

}
