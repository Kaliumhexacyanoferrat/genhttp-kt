package org.genhttp.modules.io

import org.genhttp.api.content.io.Resource
import org.genhttp.api.infrastructure.Builder
import org.genhttp.modules.io.providers.ContentProviderBuilder

/**
 * Creates a handler that will generate a response from the content
 * of the given resource.
 */
public object Content {

    /**
     * Creates a new handler that returns the content
     * of the given resource.
     *
     * @param resource The resource to be served
     */
    public fun from(resource: Builder<Resource>): ContentProviderBuilder = from(resource.build())

    /**
     * Creates a new handler that returns the content
     * of the given resource.
     *
     * @param resource The resource to be served
     */
    public fun from(resource: Resource): ContentProviderBuilder = ContentProviderBuilder().resource(resource)

}
