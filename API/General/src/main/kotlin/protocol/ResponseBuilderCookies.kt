@file:JvmName("CookieResponseExtensions")

package org.genhttp.api.protocol

import java.io.ByteArrayOutputStream
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

// Allows cookies to be added to a response via the "Set-Cookie" header.

/**
 * Adds a cookie with the given name, value and attributes to the response.
 *
 * @param name The name of the cookie
 * @param value The value of the cookie
 * @param options The attributes to be set on the cookie
 */
public fun ResponseBuilder.cookie(name: ByteString, value: ByteString, options: CookieOptions = CookieOptions()): ResponseBuilder =
    header(KnownHeaders.SetCookie, buildCookie(name, value, options))

/**
 * Adds a cookie with the given name, value and attributes to the response.
 *
 * @param name The name of the cookie
 * @param value The value of the cookie
 * @param options The attributes to be set on the cookie
 */
public fun ResponseBuilder.cookie(name: String, value: String, options: CookieOptions = CookieOptions()): ResponseBuilder =
    cookie(ByteString(name), ByteString(value), options)

/**
 * RFC 1123 date format as used by HTTP (e.g. "Tue, 03 Jan 2017 04:05:06 GMT").
 */
private val Rfc1123: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US).withZone(ZoneOffset.UTC)

private fun buildCookie(name: ByteString, value: ByteString, options: CookieOptions): ByteString {
    val buffer = ByteArrayOutputStream(name.length + 1 + value.length + 64)

    buffer.write(name)
    buffer.write('='.code)
    buffer.write(value)

    options.expires?.let {
        buffer.writeAscii("; Expires=")
        buffer.writeAscii(Rfc1123.format(it))
    }

    options.maxAge?.let {
        buffer.writeAscii("; Max-Age=")
        buffer.writeAscii(it.inWholeSeconds.toString())
    }

    options.domain?.let {
        buffer.writeAscii("; Domain=")
        buffer.write(it)
    }

    options.path?.let {
        buffer.writeAscii("; Path=")
        buffer.write(it)
    }

    if (options.secure) {
        buffer.writeAscii("; Secure")
    }

    if (options.httpOnly) {
        buffer.writeAscii("; HttpOnly")
    }

    options.sameSite?.let {
        buffer.writeAscii("; SameSite=")

        buffer.writeAscii(
            when (it) {
                SameSite.Strict -> "Strict"
                SameSite.Lax -> "Lax"
                SameSite.None -> "None"
            }
        )
    }

    return ByteString(buffer.toByteArray())
}

private fun ByteArrayOutputStream.write(value: ByteString) {
    val bytes = value.bytes
    val array = ByteArray(bytes.remaining())
    bytes.get(array)
    write(array)
}

private fun ByteArrayOutputStream.writeAscii(value: String) {
    write(value.toByteArray(Charsets.US_ASCII))
}
