package org.genhttp.modules.io.virtualtrees

import org.genhttp.api.content.io.Resource
import org.genhttp.api.content.io.ResourceContainer
import org.genhttp.api.content.io.ResourceNode
import org.genhttp.api.protocol.PathSegment

import java.time.Instant

/**
 * A node of a virtual tree that delegates to the container it has been created for.
 *
 * @param parent The container this node is a child of
 * @param name The name of the node
 * @param container The container providing the content of this node
 */
public class VirtualNode(override val parent: ResourceContainer, override val name: String, private val container: ResourceContainer) : ResourceNode {

    override val modified: Instant?
        get() = container.modified

    override suspend fun getNodes(): Collection<ResourceNode> = container.getNodes()

    override suspend fun getResources(): Collection<Resource> = container.getResources()

    override suspend fun tryGetNode(segment: PathSegment): ResourceNode? = container.tryGetNode(segment)

    override suspend fun tryGetResource(segment: PathSegment): Resource? = container.tryGetResource(segment)

}
