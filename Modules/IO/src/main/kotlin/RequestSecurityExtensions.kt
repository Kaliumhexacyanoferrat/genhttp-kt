@file:JvmName("RequestSecurityExtensions")

package org.genhttp.modules.io

import org.genhttp.api.content.ProviderException
import org.genhttp.api.protocol.PercentEncoding
import org.genhttp.api.protocol.RequestTarget
import org.genhttp.api.protocol.ResponseStatus

import java.nio.ByteBuffer

/**
 * Checks all remaining, non-routed segments of the request target for
 * path traversal attacks and throws a provider exception if one
 * is detected.
 *
 * Denies dot segments ("." and "..") as well as segments that contain
 * a path separator ("/", "\") or a null byte, either literally or
 * percent-encoded.
 *
 * @receiver The request target to be checked
 */
public fun RequestTarget.denyPathTraversal() {
    var index = 0

    while (true) {
        val segment = next(index++) ?: break

        if (isForbidden(segment.bytes)) {
            throw ProviderException(ResponseStatus.BadRequest, "Potential path traversal detected")
        }
    }
}

private fun isForbidden(segment: ByteBuffer): Boolean {
    val start = segment.position()
    val length = segment.remaining()

    var dots = 0
    var onlyDots = true

    var i = 0

    while (i < length) {
        var c = segment.get(start + i)

        if (c == '%'.code.toByte() && i + 2 < length) {
            val unescaped = PercentEncoding.tryDecode(segment.get(start + i + 1), segment.get(start + i + 2))

            if (unescaped != null) {
                c = unescaped
                i += 2
            }
        }

        if (c == '/'.code.toByte() || c == '\\'.code.toByte() || c == 0.toByte()) {
            return true
        }

        if (c == '.'.code.toByte()) {
            dots++
        } else {
            onlyDots = false
        }

        i++
    }

    return onlyDots && (dots == 1 || dots == 2)
}
