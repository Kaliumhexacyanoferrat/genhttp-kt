package org.genhttp.modules.io

import org.genhttp.api.content.io.Resource

/**
 * Calculates checksums of resources based on their meta data.
 */
public object Checksum {

    /**
     * Calculates a checksum for the given resource from its modification date and length.
     *
     * @param resource The resource to calculate the checksum for
     * @return The checksum of the resource
     */
    public fun calculate(resource: Resource): Long {
        var hash = 17L

        val length = resource.length

        hash = hash * 23 + (resource.modified?.hashCode() ?: 0).toLong()
        hash = hash * 23 + (length ?: 0)

        return hash
    }

}
