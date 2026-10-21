package org.genhttp.modules.conversion.serializers.json

import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseSink

import tools.jackson.core.StreamWriteFeature
import tools.jackson.databind.json.JsonMapper

import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * Response content that serializes an object as JSON.
 *
 * @param data The object to be serialized
 * @param mapper The mapper used to serialize the object
 */
public class JsonContent(private val data: Any, private val mapper: JsonMapper) : ResponseContent {

    // region Get-/Setters

    override val length: Long? = null

    override val type: ContentType? = ContentType.ApplicationJson

    override val encoding: ByteBuffer? = null

    // endregion

    // region Functionality

    override suspend fun calculateChecksum(): Long? = data.hashCode().toLong()

    /**
     * Writes the JSON representation of the object to the given stream.
     *
     * @param target The stream to write to (it is not closed)
     * @param bufferSize The size of the buffer to be used (unused, the JSON library handles buffering)
     */
    @Suppress("UNUSED_PARAMETER")
    public suspend fun write(target: OutputStream, bufferSize: Int) {
        writeTo(target)
    }

    override suspend fun write(sink: ResponseSink) {
        writeTo(sink.stream)
    }

    private fun writeTo(target: OutputStream) {
        mapper.writer()
            .without(StreamWriteFeature.AUTO_CLOSE_TARGET)
            .without(StreamWriteFeature.FLUSH_PASSED_TO_STREAM)
            .writeValue(target, data)
    }

    // endregion

}
