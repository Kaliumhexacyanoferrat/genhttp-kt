@file:JvmName("RangeSupportExtensions")

package org.genhttp.modules.io

import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.infrastructure.ServerHost

/**
 * Adds range support as a concern to the given builder.
 */
public fun <T : HandlerBuilder.WithConcerns<T>> T.addRangeSupport(): T {
    add(RangeSupport.create())
    return this
}

/**
 * Configures the server to respond with partial responses if
 * requested by the client.
 *
 * @receiver The host to add the feature to
 */
public fun ServerHost.rangeSupport(): ServerHost {
    add(RangeSupport.create())
    return this
}
