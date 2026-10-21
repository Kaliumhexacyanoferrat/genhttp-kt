package org.genhttp.modules.conversion.serializers.yaml

import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.ResponseBuilder
import org.genhttp.modules.conversion.serializers.ByteStreamSerialization
import org.genhttp.modules.conversion.serializers.CamelCaseNaming
import org.genhttp.modules.conversion.serializers.SerializationFormat

import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.dataformat.yaml.YAMLMapper
import tools.jackson.dataformat.yaml.YAMLWriteFeature
import tools.jackson.module.kotlin.KotlinModule

import java.io.InputStream
import java.nio.ByteBuffer

import kotlin.reflect.KType
import org.genhttp.modules.conversion.toJavaType

/**
 * Reads and writes objects as YAML. Properties are written in camel case and matched
 * case-insensitively when reading; unknown properties are not accepted.
 */
public class YamlFormat : SerializationFormat {

    override suspend fun deserialize(stream: InputStream, type: KType): Any? {
        val mapper = YamlSupport.Mapper

        return mapper.readerFor(mapper.constructType(type.toJavaType()))
            .without(StreamReadFeature.AUTO_CLOSE_SOURCE)
            .readValue(stream)
    }

    override suspend fun serialize(request: Request, response: Any): ResponseBuilder =
        request.respond().content(YamlContent(response))

    override suspend fun serialize(data: Any): ByteBuffer = ByteStreamSerialization.serialize { buffer ->
        YamlContent(data).write(buffer, 8192)
    }

}

internal object YamlSupport {

    val Mapper: YAMLMapper = YAMLMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .propertyNamingStrategy(CamelCaseNaming)
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(YAMLWriteFeature.WRITE_DOC_START_MARKER)
        .enable(YAMLWriteFeature.MINIMIZE_QUOTES)
        .build()

}
