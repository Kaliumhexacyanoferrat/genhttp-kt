@file:JvmName("ResourceTreeExtensions")

package org.genhttp.modules.io

import org.genhttp.api.content.io.Resource
import org.genhttp.api.content.io.ResourceContainer
import org.genhttp.api.content.io.ResourceNode
import org.genhttp.api.protocol.RequestTarget

/**
 * Attempts to resolve the requested node and/or resource from
 * the given container, according to the specified routing target.
 *
 * @receiver The node used to resolve the target
 * @param target The target to be resolved
 * @return A pair of the node and resource resolved from the container (or both null, if they could not be resolved)
 */
public suspend fun ResourceContainer.find(target: RequestTarget): Pair<ResourceContainer?, Resource?> {
    val current = target.current

    if (current != null) {
        val childNode: ResourceNode?

        if (target.isLast) {
            val resource = tryGetResource(current)

            if (resource != null) {
                return Pair(this, resource)
            }

            val lastNode = tryGetNode(current)

            if (lastNode != null) {
                return Pair(lastNode, null)
            }

            return Pair(null, null)
        }

        childNode = tryGetNode(current)

        if (childNode != null) {
            target.advance()
            return childNode.find(target)
        }
    }

    return Pair(this, null)
}
