@file:JvmName("RequestExtensions")

package org.genhttp.modules.io

import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.KnownHeaders
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.RequestMethod
import org.genhttp.api.protocol.getEntry

/**
 * Checks whether the request has been sent with one of the given methods.
 *
 * @param methods The methods to check for
 * @return True if the method of the request matches one of the given methods
 */
public fun Request.hasType(vararg methods: RequestMethod): Boolean {
    for (method in methods) {
        if (header.method == method) {
            return true
        }
    }

    return false
}

/**
 * Returns the value of the host header without the port, if any.
 *
 * @return The host the request has been sent to, or null if the request does not contain a host header
 */
public fun Request.getHostWithoutPort(): ByteString? {
    val host = header.headers.getEntry(KnownHeaders.Host) ?: return null

    val bytes = host.bytes

    val start = bytes.position()

    var colonIndex = -1

    for (i in 0 until bytes.remaining()) {
        if (bytes.get(start + i) == ':'.code.toByte()) {
            colonIndex = i
            break
        }
    }

    return if (colonIndex >= 0) ByteString(bytes.slice(start, colonIndex)) else host
}
