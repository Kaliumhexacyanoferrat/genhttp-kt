package org.genhttp.modules.conversion.serializers

import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.KnownHeaders
import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.getEntry

/**
 * Registers formats that can be used to serialize and
 * deserialize objects sent to or received from a
 * service oriented handler.
 *
 * @param defaultType The content type of the format to be used if the client does not request a specific one
 * @param formats The formats supported by this registry
 */
public class SerializationRegistry(defaultType: ContentType, formats: Map<ContentType, SerializationFormat>) {

    private val defaultFormat: SerializationFormat? = formats[defaultType]

    /**
     * The formats supported by this registry.
     */
    public val formats: Map<ContentType, SerializationFormat> = formats

    // region Functionality

    /**
     * Analyzes the headers of the given request to determine the format
     * that should be used to attempt to deserialize the request content.
     *
     * @param request The request to be analyzed
     * @return A serialization format to deserialize the specified content type, or the default one (if any)
     */
    public fun getDeserialization(request: Request): SerializationFormat? {
        val contentType = request.header.headers.getEntry(KnownHeaders.ContentType)

        if (contentType != null) {
            return formats[ContentType(contentType.bytes).withoutOptions()]
        }

        return defaultFormat
    }

    /**
     * Analyzes the given request to determine the format preference for serialized content
     * and attempts to provide a format that matches this preference.
     *
     * @param request The request to be analyzed
     * @param accepted The accept header as sent by the client
     * @return Either a format that can serialize into the requested format or the default format (if any)
     */
    public fun getSerialization(request: Request, accepted: ByteString?): SerializationFormat? {
        if (accepted != null) {
            return formats[ContentType(accepted.bytes)] ?: defaultFormat
        }

        return defaultFormat
    }

    /**
     * Attempts to provide a format that can be used to serialize and deserialize
     * the given content type.
     *
     * @param contentType The content type to check for
     * @return Either a format that can serialize into the requested format or the default format (if any)
     */
    public fun getFormat(contentType: String?): SerializationFormat? {
        if (contentType != null) {
            return formats[ContentType(contentType)]
        }

        return defaultFormat
    }

    // endregion

}
