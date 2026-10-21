package org.genhttp.api.content.io

import org.genhttp.api.protocol.PathSegment

import java.time.Instant

/**
 * Provides a single hierarchy level in a structure provided by a resource tree.
 */
public interface ResourceContainer {

    /**
     * The point in time when the container was modified (if known).
     */
    public val modified: Instant?

    /**
     * Tries to fetch the child node with the given name.
     *
     * @param segment The name of the node to be fetched
     * @return The node fetched from the container, if the node could be found
     */
    public suspend fun tryGetNode(segment: PathSegment): ResourceNode?

    /**
     * Returns the child nodes provided by this container.
     *
     * @return The child nodes provided by this container
     */
    public suspend fun getNodes(): Collection<ResourceNode>

    /**
     * Tries to fetch the resource with the given name.
     *
     * @param segment The name of the resource to be fetched
     * @return The resource fetched from the container, if the resource could be found
     */
    public suspend fun tryGetResource(segment: PathSegment): Resource?

    /**
     * Returns the resources provided by this container.
     *
     * @return The resources provided by this container
     */
    public suspend fun getResources(): Collection<Resource>

}
