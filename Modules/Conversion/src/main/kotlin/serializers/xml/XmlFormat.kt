package org.genhttp.modules.conversion.serializers.xml

import com.fasterxml.jackson.annotation.JsonInclude

import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.ResponseBuilder
import org.genhttp.modules.conversion.serializers.ByteStreamSerialization
import org.genhttp.modules.conversion.serializers.SerializationFormat

import tools.jackson.core.StreamReadFeature
import tools.jackson.core.StreamWriteFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.dataformat.xml.XmlMapper
import tools.jackson.dataformat.xml.XmlWriteFeature
import tools.jackson.module.kotlin.KotlinModule

import java.io.InputStream
import java.nio.ByteBuffer

import kotlin.reflect.KType
import org.genhttp.modules.conversion.toJavaType

/**
 * Reads and writes objects as XML. The name of the root element is the simple name of the class
 * of the serialized object (like XmlSerializer does).
 */
public class XmlFormat : SerializationFormat {

    override suspend fun deserialize(stream: InputStream, type: KType): Any? {
        val mapper = XmlSupport.Mapper

        return mapper.readerFor(mapper.constructType(type.toJavaType()))
            .without(StreamReadFeature.AUTO_CLOSE_SOURCE)
            .readValue(stream)
    }

    override suspend fun serialize(request: Request, response: Any): ResponseBuilder =
        request.respond().content(XmlContent(response))

    override suspend fun serialize(data: Any): ByteBuffer = ByteStreamSerialization.serialize { buffer ->
        XmlSupport.Mapper.writer()
            .without(StreamWriteFeature.AUTO_CLOSE_TARGET)
            .writeValue(buffer, data)
    }

}

internal object XmlSupport {

    val Mapper: XmlMapper = XmlMapper.builder()
        .addModule(KotlinModule.Builder().build())
        // XmlSerializer writes the (PascalCase) .NET property names, so element names are capitalized
        // and matched case-insensitively when reading
        .propertyNamingStrategy(PropertyNamingStrategies.UPPER_CAMEL_CASE)
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
        .enable(XmlWriteFeature.WRITE_XML_DECLARATION)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }
        .build()

}
