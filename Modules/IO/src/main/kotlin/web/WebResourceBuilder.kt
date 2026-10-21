package org.genhttp.modules.io.web

import org.genhttp.api.content.io.Resource
import org.genhttp.api.content.io.ResourceBuilder
import org.genhttp.api.infrastructure.BuilderMissingPropertyException
import org.genhttp.api.protocol.ContentType

import java.net.URI
import java.net.URISyntaxException
import java.time.Instant

/**
 * Builds a resource that is fetched from a HTTP(S) server.
 */
public class WebResourceBuilder : ResourceBuilder<WebResourceBuilder> {

    private var source: URI? = null

    private var fixedName: String? = null

    private var fixedType: ContentType? = null

    private var fixedModificationDate: Instant? = null

    /**
     * Sets the URI to fetch the resource from.
     *
     * @param source The URI to fetch the resource from
     */
    public fun source(source: URI): WebResourceBuilder {
        this.source = source
        return this
    }

    /**
     * Sets the URI to fetch the resource from.
     *
     * @param source The URI to fetch the resource from
     * @throws IllegalArgumentException If the given source is invalid or not absolute
     */
    public fun source(source: String): WebResourceBuilder {
        val parsed = try {
            URI(source)
        } catch (_: URISyntaxException) {
            null
        }

        if (parsed == null || !parsed.isAbsolute) {
            throw IllegalArgumentException("The given source URI is invalid or not absolute")
        }

        this.source = parsed

        return this
    }

    override fun name(name: String): WebResourceBuilder {
        fixedName = name
        return this
    }

    override fun type(contentType: ContentType): WebResourceBuilder {
        fixedType = contentType
        return this
    }

    override fun modified(modified: Instant): WebResourceBuilder {
        fixedModificationDate = modified
        return this
    }

    override fun build(): Resource {
        val source = source ?: throw BuilderMissingPropertyException("Source")

        if (!source.isAbsolute) {
            throw IllegalStateException("Only absolute URIs are supported")
        }

        if (!source.scheme.equals("http", ignoreCase = true) && !source.scheme.equals("https", ignoreCase = true)) {
            throw IllegalStateException("Only HTTP/HTTPS sources are supported")
        }

        return WebResource(source, fixedName, fixedModificationDate, fixedType)
    }

}
