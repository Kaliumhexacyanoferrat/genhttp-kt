package org.genhttp.modules.protobuf.providers

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer

import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseSink

import java.io.OutputStream
import java.nio.ByteBuffer

/**
 * Response content serializing the given object using protobuf.
 */
public class ProtobufContent(private val data: Any) : ResponseContent {

    override val length: Long?
        get() = null

    override val type: ContentType
        get() = ContentType.ApplicationProtobuf

    override val encoding: ByteBuffer?
        get() = null

    override suspend fun calculateChecksum(): Long = data.hashCode().toLong()

    /**
     * Writes the serialized object to the given stream.
     */
    public fun write(target: OutputStream) {
        target.write(serialize(data))
    }

    override suspend fun write(sink: ResponseSink) {
        sink.writer.write(ByteBuffer.wrap(serialize(data)))
    }

    internal companion object {

        @OptIn(ExperimentalSerializationApi::class)
        fun serialize(data: Any): ByteArray = ProtoBuf.encodeToByteArray(serializer(data.javaClass), data)

    }

}
