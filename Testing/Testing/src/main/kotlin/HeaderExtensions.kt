@file:JvmName("HeaderExtensions")

package org.genhttp.testing

import java.net.http.HttpResponse

/**
 * Returns the first value of the given response header, if present.
 */
public fun HttpResponse<*>.getHeader(key: String): String? = headers().firstValue(key).orElse(null)

/**
 * Returns the first value of the given content header (such as "Content-Type"), if present.
 *
 * The JDK HTTP client does not distinguish between response and content headers, so this is
 * equivalent to [getHeader].
 */
public fun HttpResponse<*>.getContentHeader(key: String): String? = getHeader(key)
