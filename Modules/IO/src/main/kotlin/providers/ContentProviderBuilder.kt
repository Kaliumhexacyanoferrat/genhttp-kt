package org.genhttp.modules.io.providers

import org.genhttp.api.content.ConcernBuilder
import org.genhttp.api.content.Concerns
import org.genhttp.api.content.Handler
import org.genhttp.api.content.HandlerBuilder
import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.BuilderMissingPropertyException

/**
 * Builds a handler that serves the content of a resource.
 */
public class ContentProviderBuilder : HandlerBuilder.WithConcerns<ContentProviderBuilder> {

    private val concerns = mutableListOf<ConcernBuilder>()

    private var resourceProvider: Resource? = null

    /**
     * Sets the resource to be served by the handler.
     *
     * @param resource The resource to be served
     */
    public fun resource(resource: Resource): ContentProviderBuilder {
        resourceProvider = resource
        return this
    }

    override fun add(concern: ConcernBuilder): ContentProviderBuilder {
        concerns.add(concern)
        return this
    }

    override fun build(): Handler {
        val resource = resourceProvider ?: throw BuilderMissingPropertyException("resourceProvider")

        return Concerns.chain(concerns, ContentProvider(resource))
    }

}
