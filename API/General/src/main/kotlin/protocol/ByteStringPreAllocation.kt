@file:JvmName("ByteStringPreAllocationExtensions")

package org.genhttp.api.protocol

import java.nio.ByteBuffer

/**
 * Allocates the backing memory of the given byte string if needed, so that the value will stay
 * available after a handler has started to read the request body.
 *
 * @param request The request the string has been read from
 * @return The (maybe) pre-allocated byte string
 */
public fun ByteString?.preAllocate(request: Request): ByteString? =
    if (this != null && request.hasBody) ByteString(copyOf(bytes)) else this

private fun copyOf(buffer: ByteBuffer): ByteArray {
    val result = ByteArray(buffer.remaining())
    buffer.get(buffer.position(), result)
    return result
}
