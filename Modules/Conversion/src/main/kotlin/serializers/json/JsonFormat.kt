package org.genhttp.modules.conversion.serializers.json

import com.fasterxml.jackson.annotation.JsonInclude

import org.genhttp.api.protocol.Request
import org.genhttp.api.protocol.ResponseBuilder
import org.genhttp.modules.conversion.serializers.ByteStreamSerialization
import org.genhttp.modules.conversion.serializers.CamelCaseNaming
import org.genhttp.modules.conversion.serializers.SerializationFormat

import tools.jackson.core.StreamReadFeature
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.cfg.EnumFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

import java.io.InputStream
import java.nio.ByteBuffer

import kotlin.reflect.KType
import kotlin.reflect.jvm.javaType

/**
 * Reads and writes objects as JSON.
 *
 * The default configuration mirrors the one of GenHTTP (System.Text.Json): property names are
 * matched case-insensitively and written in camel case, properties that are null are not written,
 * unknown properties are ignored, enums are written as numbers and the output is not indented.
 *
 * @param mapper The mapper used to read and write JSON (defaults to a mapper built from [getDefaultOptions])
 */
public class JsonFormat(mapper: JsonMapper? = null) : SerializationFormat {

    // region Get-/Setters

    /**
     * The mapper used to read and write JSON.
     */
    public val mapper: JsonMapper = mapper ?: DefaultMapper

    // endregion

    // region Functionality

    override suspend fun deserialize(stream: InputStream, type: KType): Any? =
        mapper.readerFor(mapper.constructType(type.javaType))
            .without(StreamReadFeature.AUTO_CLOSE_SOURCE)
            .readValue(stream)

    override suspend fun serialize(request: Request, response: Any): ResponseBuilder =
        request.respond().content(JsonContent(response, mapper))

    override suspend fun serialize(data: Any): ByteBuffer = ByteStreamSerialization.serialize { buffer ->
        JsonContent(data, mapper).write(buffer, 16384)
    }

    // endregion

    public companion object {

        private val DefaultMapper: JsonMapper by lazy { getDefaultOptions().build() }

        /**
         * Creates a builder for the default serializer settings that can be used
         * for further customization.
         *
         * @return A new builder configured with the default JSON options
         */
        public fun getDefaultOptions(): JsonMapper.Builder = JsonMapper.builder()
            .addModule(KotlinModule.Builder().build())
            .propertyNamingStrategy(CamelCaseNaming)
            .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(EnumFeature.WRITE_ENUMS_USING_INDEX)
            .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }

    }

}
