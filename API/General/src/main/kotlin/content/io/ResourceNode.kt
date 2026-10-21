package org.genhttp.api.content.io

/**
 * Provides a single hierarchy level in a structure provided by a resource tree.
 */
public interface ResourceNode : ResourceContainer {

    /**
     * The name of this node.
     */
    public val name: String

    /**
     * The parent of this node.
     */
    public val parent: ResourceContainer

}
