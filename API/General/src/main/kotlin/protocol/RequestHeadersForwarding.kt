@file:JvmName("ForwardingHeaderExtensions")

package org.genhttp.api.protocol

import java.net.InetAddress

// Allows the chain of reverse proxies that forwarded a request to be read from a header list
// (e.g. the "Forwarded" or "X-Forwarded-*" request headers).

/**
 * Reads the proxy chain that forwarded the request from the given headers.
 *
 * Supports both the "Forwarded" header defined by RFC 7239 and the legacy "X-Forwarded-*" headers
 * (used if no "Forwarded" header is present).
 *
 * @return One entry per hop, ordered as found in the headers
 */
public fun RequestHeaders.getForwardings(): List<Forwarding> {
    val result = mutableListOf<Forwarding>()

    var hasForwardedHeader = false

    for (i in 0 until count) {
        val (key, value) = getStringEntry(i)

        if (key == KnownHeaders.Forwarded) {
            hasForwardedHeader = true
            parseForwarded(value.toString(), result)
        }
    }

    if (!hasForwardedHeader) {
        parseLegacy(this, result)
    }

    return result
}

private fun parseForwarded(value: String, target: MutableList<Forwarding>) {
    val elements = value.split(',').filter { it.isNotEmpty() }

    for (element in elements) {
        var forAddress: InetAddress? = null
        var byAddress: InetAddress? = null
        var host: String? = null
        var protocol: ClientProtocol? = null

        val fields = element.split(';').filter { it.isNotEmpty() }

        for (field in fields) {
            val separator = field.indexOf('=')

            if (separator <= 0) {
                continue
            }

            val key = field.substring(0, separator).trim().lowercase()
            val fieldValue = field.substring(separator + 1).trim().trim('"')

            when (key) {
                "for" -> forAddress = parseNode(fieldValue)
                "by" -> byAddress = parseNode(fieldValue)
                "host" -> host = fieldValue
                "proto" -> protocol = parseProtocol(fieldValue)
            }
        }

        if (forAddress != null || byAddress != null || host != null || protocol != null) {
            target.add(Forwarding(forAddress, byAddress, host, protocol))
        }
    }
}

private fun parseLegacy(headers: KeyValueList, target: MutableList<Forwarding>) {
    val forHeader = headers.getEntry(KnownHeaders.ForwardedFor)?.toString()
    val hostHeader = headers.getEntry(KnownHeaders.ForwardedHost)?.toString()
    val protoHeader = headers.getEntry(KnownHeaders.ForwardedProto)?.toString()

    var address: InetAddress? = null

    if (forHeader != null) {
        val first = firstEntry(forHeader)

        if (first != null) {
            address = parseNode(first)
        }
    }

    val host = hostHeader?.trim()

    var protocol: ClientProtocol? = null

    if (protoHeader != null) {
        val first = firstEntry(protoHeader)

        if (first != null) {
            protocol = parseProtocol(first)
        }
    }

    if (address != null || host != null || protocol != null) {
        target.add(Forwarding(address, null, host, protocol))
    }
}

private fun firstEntry(value: String): String? =
    value.split(',').map { it.trim() }.firstOrNull { it.isNotEmpty() }

private fun parseNode(raw: String): InetAddress? {
    var value = raw.trim().trim('"')

    if (value.isEmpty()) {
        return null
    }

    if (value[0] == '[') {
        val end = value.indexOf(']')

        if (end > 0) {
            value = value.substring(1, end)
        }
    } else {
        val colon = value.indexOf(':')

        if (colon > 0 && value.indexOf(':', colon + 1) < 0) {
            value = value.substring(0, colon)
        }
    }

    // only accepts IP literals, never performs a name lookup
    return try {
        InetAddress.ofLiteral(value)
    } catch (_: IllegalArgumentException) {
        null
    }
}

private fun parseProtocol(protocol: String?): ClientProtocol? {
    if (protocol != null) {
        if (protocol.equals("https", ignoreCase = true)) {
            return ClientProtocol.Https
        }

        if (protocol.equals("http", ignoreCase = true)) {
            return ClientProtocol.Http
        }
    }

    return null
}
