@file:JvmName("RequestSecurityExtensions")

package org.genhttp.modules.io

import org.genhttp.api.content.ProviderException
import org.genhttp.api.protocol.RequestTarget
import org.genhttp.api.protocol.ResponseStatus

import java.nio.ByteBuffer

/**
 * Checks all remaining, non-routed segments of the request target for
 * path traversal attacks and throws a provider exception if one
 * is detected.
 *
 * @receiver The request target to be checked
 */
public fun RequestTarget.denyPathTraversal() {
    var index = 0

    while (true) {
        val segment = next(index++) ?: break

        if (isDotSegment(segment.bytes)) {
            throw ProviderException(ResponseStatus.BadRequest, "Potential path traversal detected")
        }
    }
}

private fun isDotSegment(segment: ByteBuffer): Boolean {
    val start = segment.position()
    val length = segment.remaining()

    var dots = 0

    var i = 0

    while (i < length) {
        val current = segment.get(start + i)

        if (current == '.'.code.toByte()) {
            dots++
            i++
        } else if (current == '%'.code.toByte() && i + 2 < length && segment.get(start + i + 1) == '2'.code.toByte() &&
            (segment.get(start + i + 2) == 'e'.code.toByte() || segment.get(start + i + 2) == 'E'.code.toByte())
        ) {
            dots++
            i += 3
        } else {
            return false
        }
    }

    return dots == 1 || dots == 2
}
