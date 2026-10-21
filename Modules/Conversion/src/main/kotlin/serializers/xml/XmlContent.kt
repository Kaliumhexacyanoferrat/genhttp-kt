package org.genhttp.modules.conversion.serializers.xml

import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseSink

import tools.jackson.core.StreamWriteFeature

import java.nio.ByteBuffer

/**
 * Response content that serializes an object as XML.
 *
 * @param data The object to be serialized
 */
public class XmlContent(private val data: Any) : ResponseContent {

    // region Get-/Setters

    override val length: Long? = null

    override val type: ContentType? = ContentType.TextXml

    override val encoding: ByteBuffer? = null

    // endregion

    // region Functionality

    override suspend fun calculateChecksum(): Long? = data.hashCode().toLong()

    override suspend fun write(sink: ResponseSink) {
        XmlSupport.Mapper.writer()
            .without(StreamWriteFeature.AUTO_CLOSE_TARGET)
            .without(StreamWriteFeature.FLUSH_PASSED_TO_STREAM)
            .writeValue(sink.stream, data)
    }

    // endregion

}
