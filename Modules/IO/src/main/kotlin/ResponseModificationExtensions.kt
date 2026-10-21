@file:JvmName("ResponseModificationExtensions")

package org.genhttp.modules.io

import org.genhttp.api.protocol.ResponseBuilder

/**
 * Applies the given modifications to the response.
 *
 * @receiver The response to be modified
 * @param modifications The modifications to be applied
 */
public fun ResponseBuilder.apply(modifications: (ResponseBuilder.() -> Unit)?): ResponseBuilder {
    modifications?.invoke(this)
    return this
}
