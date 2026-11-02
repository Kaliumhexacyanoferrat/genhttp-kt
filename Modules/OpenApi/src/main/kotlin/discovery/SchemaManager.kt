package org.genhttp.modules.openapi.discovery

import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.core.jackson.ModelResolver
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema

import org.genhttp.api.protocol.ContentType
import org.genhttp.modules.conversion.serializers.SerializationRegistry
import org.genhttp.modules.conversion.serializers.json.JsonFormat
import org.genhttp.modules.conversion.toJavaType

import tools.jackson.databind.json.JsonMapper

import java.lang.reflect.Type
import java.util.IdentityHashMap

import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * Generates the JSON schemas of the types used by an API and registers
 * the types referenced by them with the document.
 *
 * Types that are no primitives (such as models) are registered with the components of the document and
 * a reference to them is returned. Nullable types are marked as such. The properties of models are described
 * as they are written by the JSON mapper the schema is generated for (e.g. following its naming strategy).
 */
public class SchemaManager internal constructor(private val document: OpenAPI) {

    private val convertersByMapper = IdentityHashMap<JsonMapper, ModelConverters>()

    /**
     * Generates or retrieves a JSON schema that represents the given type,
     * using the default JSON serialization settings of the framework.
     *
     * @param type The type to generate a schema for
     * @return The generated or retrieved JSON schema
     */
    public fun getOrCreateSchema(type: KType): Schema<*> = getOrCreateSchema(type, DefaultMapper)

    /**
     * Generates or retrieves a JSON schema that represents the given type
     * as it will be serialized by the JSON format of the given registry.
     *
     * @param type The type to generate a schema for
     * @param serialization The registry used to serialize and deserialize instances of the type
     * @return The generated or retrieved JSON schema
     */
    public fun getOrCreateSchema(type: KType, serialization: SerializationRegistry): Schema<*> = getOrCreateSchema(type, getMapper(serialization))

    /**
     * Generates or retrieves a JSON schema that represents the given type
     * as it will be serialized with the given mapper.
     *
     * Schemas are shared by all operations of the document, so a type will be described
     * with the mapper it has been encountered with first.
     *
     * @param type The type to generate a schema for
     * @param mapper The mapper used to serialize and deserialize instances of the type
     * @return The generated or retrieved JSON schema
     */
    public fun getOrCreateSchema(type: KType, mapper: JsonMapper): Schema<*> {
        val schema = resolve(type.toJavaType(), mapper)

        return if (type.isMarkedNullable) schema.asNullable() else schema
    }

    /**
     * Generates or retrieves a JSON schema that represents the given type,
     * using the default JSON serialization settings of the framework.
     *
     * @param type The type to generate a schema for
     * @return The generated or retrieved JSON schema
     */
    public fun getOrCreateSchema(type: KClass<*>): Schema<*> = getOrCreateSchema(type, DefaultMapper)

    /**
     * Generates or retrieves a JSON schema that represents the given type
     * as it will be serialized by the JSON format of the given registry.
     *
     * @param type The type to generate a schema for
     * @param serialization The registry used to serialize and deserialize instances of the type
     * @return The generated or retrieved JSON schema
     */
    public fun getOrCreateSchema(type: KClass<*>, serialization: SerializationRegistry): Schema<*> = getOrCreateSchema(type, getMapper(serialization))

    /**
     * Generates or retrieves a JSON schema that represents the given type
     * as it will be serialized with the given mapper.
     *
     * Schemas are shared by all operations of the document, so a type will be described
     * with the mapper it has been encountered with first.
     *
     * @param type The type to generate a schema for
     * @param mapper The mapper used to serialize and deserialize instances of the type
     * @return The generated or retrieved JSON schema
     */
    public fun getOrCreateSchema(type: KClass<*>, mapper: JsonMapper): Schema<*> = resolve(type.javaObjectType, mapper)

    private fun resolve(type: Type, mapper: JsonMapper): Schema<*> {
        val resolved = getConverters(mapper).resolveAsResolvedSchema(AnnotatedType(type).resolveAsRef(true))

        val referenced = resolved.referencedSchemas

        if (!referenced.isNullOrEmpty()) {
            val components = document.components ?: Components().also { document.components = it }

            for ((name, schema) in referenced) {
                if (components.schemas?.containsKey(name) != true) {
                    components.addSchemas(name, schema)
                }
            }
        }

        return resolved.schema ?: Schema<Any>()
    }

    private fun getConverters(mapper: JsonMapper): ModelConverters = convertersByMapper.getOrPut(mapper) {
        val names = SerializedNames(mapper)

        val result = ModelConverters(false)

        // analyze the types with a mapper that names their properties like the given mapper
        result.converters.filterIsInstance<ModelResolver>().forEach { result.removeConverter(it) }

        result.addConverter(ModelResolver(names.createSchemaMapper()))
        result.addConverter(DocumentationSchemaProcessor(names))

        result
    }

    private companion object {

        val DefaultMapper: JsonMapper
            get() = JsonFormat().mapper

        fun getMapper(serialization: SerializationRegistry): JsonMapper {
            val preferred = serialization.formats[ContentType.ApplicationJson]

            if (preferred is JsonFormat) {
                return preferred.mapper
            }

            return serialization.formats.values.filterIsInstance<JsonFormat>().firstOrNull()?.mapper ?: DefaultMapper
        }

    }

}

/**
 * Marks the schema as nullable. As siblings of a reference are ignored by OpenAPI 3.0, references are
 * wrapped.
 */
internal fun Schema<*>.asNullable(): Schema<*> {
    if (`$ref` != null) {
        return Schema<Any>().also {
            it.nullable = true
            it.addOneOfItem(this)
        }
    }

    nullable = true

    return this
}
