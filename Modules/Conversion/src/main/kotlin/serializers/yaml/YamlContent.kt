package org.genhttp.modules.conversion.serializers.yaml

import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseSink

import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * Response content that serializes an object as YAML.
 *
 * @param data The object to be serialized
 */
public class YamlContent(public val data: Any) : ResponseContent {

    // region Get-/Setters

    override val length: Long? = null

    override val type: ContentType? = ContentType.ApplicationYaml

    override val encoding: ByteBuffer? = null

    // endregion

    // region Functionality

    override suspend fun calculateChecksum(): Long? = data.hashCode().toLong()

    /**
     * Writes the YAML representation of the object to the given stream.
     *
     * @param target The stream to write to (it is not closed)
     * @param bufferSize The size of the buffer to be used (unused, the YAML library handles buffering)
     */
    @Suppress("UNUSED_PARAMETER")
    public suspend fun write(target: OutputStream, bufferSize: Int) {
        target.write(YamlSupport.Mapper.writeValueAsBytes(data))
    }

    override suspend fun write(sink: ResponseSink) {
        sink.stream.write(YamlSupport.Mapper.writeValueAsBytes(data))
    }

    // endregion

}
