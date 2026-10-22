package org.genhttp.modules.openapi.discovery

import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.media.Schema

import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

/**
 * Adds the documentation declared on model types and their
 * properties to the generated JSON schemas, and marks properties of nullable types as such.
 *
 * For classes with a primary constructor, the documentation may also be placed
 * on the parameters of the constructor.
 */
internal class DocumentationSchemaProcessor : ModelConverter {

    override fun resolve(type: AnnotatedType, context: ModelConverterContext, chain: Iterator<ModelConverter>): Schema<*>? {
        if (!chain.hasNext()) {
            return null
        }

        val schema = chain.next().resolve(type, context, chain) ?: return null

        val raw = runCatching { Json.mapper().constructType(type.type).rawClass }.getOrNull() ?: return schema

        if (!isModel(raw)) {
            return schema
        }

        val target = findModel(schema, context) ?: return schema

        val kotlinType = runCatching { raw.kotlin }.getOrNull() ?: return schema

        process(kotlinType, target)

        return schema
    }

    private fun isModel(type: Class<*>): Boolean =
        !type.isPrimitive && !type.isArray && !type.isEnum && !type.name.startsWith("java.") && !type.name.startsWith("kotlin.")

    /**
     * Finds the schema describing the type, which is either the schema itself or the one a reference points to.
     */
    private fun findModel(schema: Schema<*>, context: ModelConverterContext): Schema<*>? {
        val ref = schema.`$ref` ?: return schema

        return context.definedModels[ref.substringAfterLast('/')]
    }

    private fun process(type: KClass<*>, schema: Schema<*>) {
        runCatching {
            Documentation.getDescription(type)?.let { schema.description = it }

            val properties = schema.properties

            if (!properties.isNullOrEmpty()) {
                val parameters = getConstructorParameters(type)

                for (property in type.memberProperties) {
                    val key = properties.keys.firstOrNull { it.equals(property.name, ignoreCase = true) } ?: continue

                    var description = Documentation.getDescription(property)

                    if (description == null) {
                        description = parameters[property.name]?.let { Documentation.getSummary(it) }
                    }

                    if (description != null) {
                        properties[key]?.description = description
                    }

                    if (property.returnType.isMarkedNullable) {
                        properties[key]?.let { properties[key] = it.asNullable() }
                    }
                }
            }
        }
    }

    private fun getConstructorParameters(type: KClass<*>): Map<String, KParameter> {
        val result = HashMap<String, KParameter>()

        val constructor = type.primaryConstructor ?: return result

        for (parameter in constructor.parameters) {
            parameter.name?.let { result.putIfAbsent(it, parameter) }
        }

        return result
    }

}
