package org.genhttp.modules.conversion.serializers

import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.ResponseBuilder

import java.io.InputStream
import java.nio.ByteBuffer

import kotlin.reflect.KType

/**
 * Implements a specific format that can be used to serialize
 * and deserialize data within service oriented handler.
 *
 * Instances implementing this interface can be registered at a
 * [SerializationRegistry] to support additional transport formats.
 */
public interface SerializationFormat {

    /**
     * Deserializes the given stream into an object of the given type.
     *
     * @param stream The stream providing the data to be deserialized
     * @param type The type to be deserialized
     * @return The object deserialized from the given stream
     */
    public suspend fun deserialize(stream: InputStream, type: KType): Any?

    /**
     * Serializes the given response into a new response for the
     * given request.
     *
     * @param request The request to generate a response for
     * @param response The object to be serialized
     * @return The response representing the serialized object
     */
    public suspend fun serialize(request: Request, response: Any): ResponseBuilder

    /**
     * Serializes the given payload into a memory buffer.
     *
     * @param data The data to be processed
     * @return The byte representation of the serialized data (a read-only buffer)
     */
    public suspend fun serialize(data: Any): ByteBuffer

}
