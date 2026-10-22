package org.genhttp.modules.openapi.discovery

import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema

import org.genhttp.modules.conversion.toJavaType

import java.lang.reflect.Type

import kotlin.reflect.KClass
import kotlin.reflect.KType

/**
 * Generates the JSON schemas of the types used by an API and registers
 * the types referenced by them with the document.
 */
public class SchemaManager internal constructor(private val document: OpenAPI) {

    private val converters = ModelConverters(false).apply {
        addConverter(DocumentationSchemaProcessor())
    }

    /**
     * Generates or retrieves a JSON schema that represents the given type.
     *
     * Types that are no primitives (such as models) are registered with the components of the document and
     * a reference to them is returned. Nullable types are marked as such.
     *
     * @param type The type to generate a schema for
     * @return The generated or retrieved JSON schema
     */
    public fun getOrCreateSchema(type: KType): Schema<*> {
        val schema = resolve(type.toJavaType())

        return if (type.isMarkedNullable) schema.asNullable() else schema
    }

    /**
     * Generates or retrieves a JSON schema that represents the given type.
     *
     * @param type The type to generate a schema for
     * @return The generated or retrieved JSON schema
     */
    public fun getOrCreateSchema(type: KClass<*>): Schema<*> = resolve(type.javaObjectType)

    private fun resolve(type: Type): Schema<*> {
        val resolved = converters.resolveAsResolvedSchema(AnnotatedType(type).resolveAsRef(true))

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
