package org.genhttp.api.content.io

import org.genhttp.api.GenHttpDsl
import org.genhttp.api.infrastructure.Builder
import org.genhttp.api.protocol.ContentType

import java.time.Instant

/**
 * When implemented by builders providing resource instances, this interface allows to configure common
 * properties of resources in a unified way.
 */
@GenHttpDsl
public interface ResourceBuilder<out T : ResourceBuilder<T>> : Builder<Resource> {

    /**
     * Sets the name of the resource.
     *
     * @param name The name of the resource
     */
    public fun name(name: String): T

    /**
     * Sets the content type of the resource.
     *
     * @param contentType The content type of the resource
     */
    public fun type(contentType: ContentType): T

    /**
     * Sets the modification date and time of the resource.
     *
     * @param modified The modification date and time of the resource
     */
    public fun modified(modified: Instant): T

}
