package org.genhttp.modules.reflection.routing

import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.PercentEncoding

/**
 * Collects the arguments read from the request path while matching a route.
 */
public class PathArgumentSink {

    internal var arguments: MutableMap<ByteString, ByteString>? = null

    /**
     * Adds the given (percent-encoded) value for the given argument.
     */
    public fun add(key: ByteString, value: ByteString) {
        val map = arguments ?: HashMap<ByteString, ByteString>().also { arguments = it }
        map[key] = ByteString(PercentEncoding.decode(value.bytes))
    }

}
