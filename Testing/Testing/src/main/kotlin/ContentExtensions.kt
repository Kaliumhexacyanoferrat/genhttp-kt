@file:JvmName("ContentExtensions")

package org.genhttp.testing

import org.genhttp.modules.conversion.Serialization
import org.genhttp.modules.protobuf.addProtobuf

import java.io.ByteArrayInputStream
import java.net.http.HttpResponse
import java.nio.charset.Charset

import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Reads the body of the response as a string (using the charset of the content type, UTF-8 by default).
 */
public fun HttpResponse<ByteArray>.getContent(): String {
    val charset = getContentHeader("Content-Type")
        ?.split(';')
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
        ?.substringAfter('=')
        ?.trim('"')
        ?.let { runCatching { Charset.forName(it) }.getOrNull() }
        ?: Charsets.UTF_8

    return String(body(), charset)
}

/**
 * Deserializes the body of the response into the given type, using the format indicated by the content type.
 *
 * @throws IllegalStateException if the server did not return a result
 */
public suspend inline fun <reified T> HttpResponse<ByteArray>.getContent(): T =
    getOptionalContent<T>() ?: throw IllegalStateException("Server did not return a result")

/**
 * Deserializes the body of the response into the given type, using the format indicated by the content type.
 *
 * @return The deserialized content or null, if the server responded with 204 No Content
 */
public suspend inline fun <reified T> HttpResponse<ByteArray>.getOptionalContent(): T? =
    getOptionalContent(typeOf<T>()) as T?

/**
 * Deserializes the body of the response into the given type, using the format indicated by the content type.
 *
 * @return The deserialized content or null, if the server responded with 204 No Content
 */
public suspend fun HttpResponse<ByteArray>.getOptionalContent(type: KType): Any? {
    if (statusCode() == 204) {
        return null
    }

    val contentType = getContentHeader("Content-Type")

    val registry = Serialization.default()
        .addProtobuf()
        .build()

    val format = registry.getFormat(contentType)
        ?: throw IllegalStateException("Unable to find deserializer for content type '$contentType'")

    return ByteArrayInputStream(body()).use { format.deserialize(it, type) }
}
