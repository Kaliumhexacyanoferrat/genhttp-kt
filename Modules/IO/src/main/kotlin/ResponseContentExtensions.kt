@file:JvmName("ResponseContentExtensions")

package org.genhttp.modules.io

import org.genhttp.api.content.io.Resource
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseBuilder
import org.genhttp.modules.io.streaming.ByteArrayContent
import org.genhttp.modules.io.streaming.MemoryContent
import org.genhttp.modules.io.streaming.ResourceContent
import org.genhttp.modules.io.streaming.StreamContent
import org.genhttp.modules.io.strings.StringContent

import java.io.InputStream
import java.nio.ByteBuffer

/**
 * Sets the given text (UTF-8 encoded) as the content of the response.
 */
public fun ResponseBuilder.content(text: String, contentType: ContentType? = null): ResponseBuilder =
    content(StringContent(text, contentType))

/**
 * Sets the content of the given resource as the content of the response.
 */
public fun ResponseBuilder.content(resource: Resource, contentType: ContentType? = null): ResponseBuilder =
    content(ResourceContent(resource, contentType))

/**
 * Sets the given stream as the content of the response. The stream is closed after it has been written.
 */
public fun ResponseBuilder.content(
    stream: InputStream,
    contentType: ContentType? = null,
    knownLength: Long? = null,
    encoding: ByteBuffer? = null,
    checksumProvider: (suspend () -> Long?)? = null
): ResponseBuilder = content(StreamContent(stream, contentType, knownLength, encoding, checksumProvider))

/**
 * Sets the given bytes as the content of the response.
 */
public fun ResponseBuilder.content(
    data: ByteArray,
    contentType: ContentType? = null,
    checksumProvider: (suspend () -> Long?)? = null
): ResponseBuilder = content(ByteArrayContent(data, contentType, checksumProvider))

/**
 * Sets the given buffer (from its position to its limit) as the content of the response.
 */
public fun ResponseBuilder.content(
    data: ByteBuffer,
    contentType: ContentType? = null,
    checksumProvider: (suspend () -> Long?)? = null
): ResponseBuilder = content(MemoryContent(data, contentType, checksumProvider))
