package org.genhttp.modules.reflection.routing

import org.genhttp.api.protocol.ByteString

/**
 * The result of matching a request target against a route.
 *
 * @property offset The number of segments the target needs to be advanced by
 * @property pathArguments The arguments read from the path, if any
 */
public class RoutingMatch(
    public val offset: Int,
    public val pathArguments: Map<ByteString, ByteString>?
)
