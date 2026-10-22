package org.genhttp.modules.io.web

import kotlinx.coroutines.future.await

import org.genhttp.api.content.io.Resource
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseSink
import org.genhttp.modules.io.Checksum
import org.genhttp.modules.io.streaming.copyPooled

import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * A resource that is fetched from a HTTP(S) server on demand.
 *
 * @param source The URI to fetch the resource from
 * @param name The name of the resource (defaults to the file name of the URI)
 * @param modified The modification date of the resource (taken from the server response if not specified)
 * @param contentType The type of the resource (taken from the server response if not specified)
 */
public class WebResource(public val source: URI, name: String?, modified: Instant?, contentType: ContentType?) : Resource {

    private val fixedModified: Instant? = modified

    private val fixedContentType: ContentType? = contentType

    @Volatile
    private var actualModificationDate: Instant? = null

    @Volatile
    private var actualContentType: ContentType? = null

    @Volatile
    private var actualContentLength: Long? = null

    override val name: String? = name ?: (source.path ?: "").substringAfterLast('/')

    override val modified: Instant?
        get() = fixedModified ?: actualModificationDate

    override val contentType: ContentType?
        get() = fixedContentType ?: actualContentType

    override val length: Long?
        get() = actualContentLength

    override suspend fun calculateChecksum(): Long {
        // HEAD() does not send a "Content-Length: 0" header, which strict servers reject
        val request = HttpRequest.newBuilder(source).HEAD().build()

        val response = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).await()

        response.body().use {
            ensureSuccess(response)

            updateFields(response)

            val tag = response.headers().firstValue("ETag").orElse(null)

            if (tag != null) {
                return tag.removePrefix("W/").hashCode().toLong()
            }
        }

        return Checksum.calculate(this)
    }

    override suspend fun getContent(): InputStream {
        val request = HttpRequest.newBuilder(source).GET().build()

        val response = client.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).await()

        try {
            ensureSuccess(response)
        } catch (e: IOException) {
            response.body().close()
            throw e
        }

        updateFields(response)

        return response.body()
    }

    override suspend fun write(sink: ResponseSink) {
        getContent().use { stream ->
            stream.copyPooled(sink.stream, 8192)
        }
    }

    private fun ensureSuccess(response: HttpResponse<*>) {
        if (response.statusCode() !in 200..299) {
            throw IOException("Response status code does not indicate success: ${response.statusCode()}")
        }
    }

    private fun updateFields(response: HttpResponse<*>) {
        val headers = response.headers()

        actualModificationDate = headers.firstValue("Last-Modified").map(::parseDate).orElse(null)

        actualContentType = headers.firstValue("Content-Type").map { ContentType(it) }.orElse(null)

        actualContentLength = headers.firstValueAsLong("Content-Length").let { if (it.isPresent) it.asLong else null }
    }

    private fun parseDate(value: String): Instant? = try {
        ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()
    } catch (_: DateTimeParseException) {
        null
    }

    private companion object {

        private val client: HttpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()

    }

}
