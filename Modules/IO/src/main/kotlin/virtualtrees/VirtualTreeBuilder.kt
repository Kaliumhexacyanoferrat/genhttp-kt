package org.genhttp.modules.io.virtualtrees

import org.genhttp.api.content.io.Resource
import org.genhttp.api.content.io.ResourceContainer
import org.genhttp.api.content.io.ResourceNode
import org.genhttp.api.content.io.ResourceTree
import org.genhttp.api.infrastructure.Builder
import org.genhttp.api.protocol.PathSegment

/**
 * Builds a tree that combines other trees, containers and resources.
 */
public class VirtualTreeBuilder : Builder<ResourceTree> {

    private val nodes = LinkedHashMap<PathSegment, (ResourceContainer) -> ResourceNode>()

    private val resources = LinkedHashMap<PathSegment, Resource>()

    /**
     * Adds the given container with the specified name to the tree.
     *
     * @param name The name of the node to be added
     * @param container The container to be added
     */
    public fun add(name: String, container: ResourceContainer): VirtualTreeBuilder {
        put(nodes, PathSegment(name)) { parent -> VirtualNode(parent, name, container) }
        return this
    }

    /**
     * Adds the given container with the specified name to the tree.
     *
     * @param name The name of the node to be added
     * @param tree The container to be added
     */
    public fun add(name: String, tree: Builder<ResourceTree>): VirtualTreeBuilder = add(name, tree.build())

    /**
     * Adds the given resource with the specified name to the tree.
     *
     * @param name The name of the resource to be added
     * @param resource The resource to be added
     */
    public fun add(name: String, resource: Resource): VirtualTreeBuilder {
        put(resources, PathSegment(name), resource)
        return this
    }

    /**
     * Adds the given resource with the specified name to the tree.
     *
     * @param name The name of the resource to be added
     * @param resource The resource to be added
     */
    @JvmName("addResourceBuilder")
    public fun add(name: String, resource: Builder<Resource>): VirtualTreeBuilder {
        put(resources, PathSegment(name), resource.build())
        return this
    }

    override fun build(): ResourceTree = VirtualTree(nodes, resources)

    private fun <T> put(target: MutableMap<PathSegment, T>, key: PathSegment, value: T) {
        require(!target.containsKey(key)) { "An item with the key '$key' has already been added" }

        target[key] = value
    }

}
