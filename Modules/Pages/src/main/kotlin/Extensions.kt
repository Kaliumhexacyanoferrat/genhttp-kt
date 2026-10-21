@file:JvmName("Extensions")

package org.genhttp.modules.pages

import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.ResponseBuilder
import org.genhttp.modules.io.strings.StringContent

private val HtmlContentType = ContentType("text/html; charset=\"utf-8\"")

/**
 * Creates a response that can be returned by a handler to serve
 * a HTML page.
 *
 * @param content The HTML page to be served
 * @return The HTML page response
 */
public fun Request.getPage(content: String): ResponseBuilder = respond().content(StringContent(content, HtmlContentType))

/**
 * Escapes the given string so it can safely be used in HTML.
 *
 * Behaves like `HttpUtility.HtmlEncode` of .NET: the characters `&`, `<`, `>`, `"` and `'` are replaced
 * by their entities, characters from U+00A0 to U+00FF and supplementary characters (surrogate pairs)
 * are written as numeric character references.
 *
 * @return The escaped version of the string
 */
public fun String.escaped(): String {
    val result = StringBuilder(length + 16)

    var i = 0

    while (i < length) {
        val ch = this[i]

        when {
            ch == '<' -> result.append("&lt;")
            ch == '>' -> result.append("&gt;")
            ch == '"' -> result.append("&quot;")
            ch == '\'' -> result.append("&#39;")
            ch == '&' -> result.append("&amp;")
            ch.code in 160..255 -> result.append("&#").append(ch.code).append(';')
            ch.isHighSurrogate() && i + 1 < length && this[i + 1].isLowSurrogate() -> {
                result.append("&#").append(Character.toCodePoint(ch, this[i + 1])).append(';')
                i++
            }
            else -> result.append(ch)
        }

        i++
    }

    return result.toString()
}
