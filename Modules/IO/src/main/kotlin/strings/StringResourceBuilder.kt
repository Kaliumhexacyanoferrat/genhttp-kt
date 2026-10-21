package org.genhttp.modules.io.strings

import org.genhttp.api.content.io.Resource
import org.genhttp.api.content.io.ResourceBuilder
import org.genhttp.api.infrastructure.BuilderMissingPropertyException
import org.genhttp.api.protocol.ContentType

import java.time.Instant

/**
 * Builds a resource that is backed by a string.
 */
public class StringResourceBuilder : ResourceBuilder<StringResourceBuilder> {

    private var content: String? = null

    private var name: String? = null

    private var contentType: ContentType? = null

    private var modified: Instant? = null

    /**
     * Sets the text to be provided by the resource.
     *
     * @param content The string that will be returned by the resource (UTF-8-encoded)
     */
    public fun content(content: String): StringResourceBuilder {
        this.content = content
        return this
    }

    override fun name(name: String): StringResourceBuilder {
        this.name = name
        return this
    }

    override fun type(contentType: ContentType): StringResourceBuilder {
        this.contentType = contentType
        return this
    }

    override fun modified(modified: Instant): StringResourceBuilder {
        this.modified = modified
        return this
    }

    override fun build(): Resource {
        val content = content ?: throw BuilderMissingPropertyException("content")

        return StringResource(content, name, contentType, modified)
    }

}
