package org.genhttp.modules.conversion.serializers

import org.genhttp.api.infrastructure.Builder
import org.genhttp.api.infrastructure.BuilderMissingPropertyException
import org.genhttp.api.protocol.ContentType

/**
 * Configures the formats supported by a service oriented handler.
 */
public class SerializationBuilder : Builder<SerializationRegistry> {

    private val registry = LinkedHashMap<ContentType, SerializationFormat>()

    private var default: ContentType? = null

    // region Functionality

    /**
     * Sets the content type of the format to be used if the client does not request a specific one.
     *
     * @param contentType The content type of the default format
     */
    public fun default(contentType: ContentType): SerializationBuilder {
        default = contentType
        return this
    }

    /**
     * Registers the given format for the given content type.
     *
     * @param contentType The content type to be handled by the format
     * @param format The format to be used
     */
    public fun add(contentType: ContentType, format: SerializationFormat): SerializationBuilder {
        registry[contentType] = format
        return this
    }

    override fun build(): SerializationRegistry =
        SerializationRegistry(default ?: throw BuilderMissingPropertyException("default"), registry.toMap())

    // endregion

}
