package org.genhttp.modules.files.single

import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Concerns
import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.content.io.Resource
import org.genhttp.api.protocol.ContentType

/**
 * Builds a handler that serves a single resource.
 *
 * @param resource The resource to be served
 */
public class ResourceAssetBuilder(private val resource: Resource) : HandlerBuilder.WithConcerns<ResourceAssetBuilder> {

    private val concerns = mutableListOf<ConcernBuilder>()

    private var asDownload = false

    private var fileName: String? = null

    private var contentType: ContentType? = null

    /**
     * Instructs the client to download the resource instead of displaying it.
     *
     * @param fileName The name of the file to be suggested to the client (defaults to the name of the resource)
     */
    public fun asDownload(fileName: String? = null): ResourceAssetBuilder {
        asDownload = true
        this.fileName = fileName
        return this
    }

    /**
     * Sets the content type of the resource (guessed from the resource if not specified).
     *
     * @param type The type of the content
     */
    public fun type(type: ContentType): ResourceAssetBuilder {
        contentType = type
        return this
    }

    override fun add(concern: ConcernBuilder): ResourceAssetBuilder {
        concerns.add(concern)
        return this
    }

    override fun build(): Handler =
        Concerns.chain(concerns, ResourceAssetHandler(resource, asDownload, fileName, contentType))

}
