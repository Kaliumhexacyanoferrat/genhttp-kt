package org.genhttp.modules.security.cors

import org.genhttp.api.protocol.RequestMethod

/**
 * Specifies how a browser may interact with the server on behalf of a specific origin.
 *
 * @property allowedMethods The HTTP methods the client is allowed to access (any, if null)
 * @property allowedHeaders The headers a client may send to the server (any, if null)
 * @property exposedHeaders The headers that will be accessible by the client (any, if null)
 * @property allowCredentials Whether the client is allowed to read credentials from the request
 * @property maxAge The duration in seconds this policy is valid for (unsigned in GenHTTP, must not be negative)
 */
public data class OriginPolicy(
    val allowedMethods: List<RequestMethod>?,
    val allowedHeaders: List<String>?,
    val exposedHeaders: List<String>?,
    val allowCredentials: Boolean,
    val maxAge: Long
)
