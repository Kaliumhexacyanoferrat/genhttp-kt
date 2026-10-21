package org.genhttp.modules.conversion.serializers

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Helper to serialize data into memory.
 */
public object ByteStreamSerialization {

    /**
     * Allows consumers to write a serialized object representation to a memory stream
     * that will then be converted into a read only memory.
     *
     * @param writer The logic that will perform the serialization
     * @return The byte representation of the serialized data
     */
    public suspend fun serialize(writer: suspend (ByteArrayOutputStream) -> Unit): ByteBuffer {
        val buffer = ByteArrayOutputStream()

        buffer.use {
            writer(it)
        }

        return ByteBuffer.wrap(buffer.toByteArray()).asReadOnlyBuffer()
    }

}
