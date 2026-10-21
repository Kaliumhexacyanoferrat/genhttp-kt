package org.genhttp.modules.protobuf.providers

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import kotlinx.serialization.serializer

import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.ResponseBuilder
import org.genhttp.modules.conversion.serializers.ByteStreamSerialization
import org.genhttp.modules.conversion.serializers.SerializationFormat

import java.io.InputStream
import java.nio.ByteBuffer

import kotlin.reflect.KType

/**
 * Serialization format for protobuf, based on kotlinx.serialization.
 */
public class ProtobufFormat : SerializationFormat {

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun deserialize(stream: InputStream, type: KType): Any? =
        ProtoBuf.decodeFromByteArray(serializer(type), stream.readAllBytes())

    override suspend fun serialize(request: Request, response: Any): ResponseBuilder =
        request.respond().content(ProtobufContent(response))

    override suspend fun serialize(data: Any): ByteBuffer =
        ByteStreamSerialization.serialize { ProtobufContent(data).write(it) }

}
