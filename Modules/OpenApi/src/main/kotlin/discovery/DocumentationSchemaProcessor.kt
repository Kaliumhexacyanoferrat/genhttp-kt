package org.genhttp.modules.openapi.discovery

import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.media.Schema

import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaGetter

/**
 * Adds the documentation declared on model types and their
 * properties to the generated JSON schemas, and marks properties of nullable types as such.
 * Properties that are not written by the JSON mapper the schema is generated for are removed.
 *
 * For classes with a primary constructor, the documentation may also be placed
 * on the parameters of the constructor.
 *
 * @param names The names of the properties as written by the JSON mapper
 */
internal class DocumentationSchemaProcessor(private val names: SerializedNames) : ModelConverter {

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

        process(raw, kotlinType, target)

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

    private fun process(raw: Class<*>, type: KClass<*>, schema: Schema<*>) {
        runCatching {
            Documentation.getDescription(type)?.let { schema.description = it }

            val properties = schema.properties

            if (!properties.isNullOrEmpty()) {
                removeUnwritten(raw, schema, properties)

                val parameters = getConstructorParameters(type)

                for (property in type.memberProperties) {
                    val key = getPropertyName(raw, property) ?: continue

                    if (!properties.containsKey(key)) {
                        continue
                    }

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

    /**
     * The name the property is written with by the JSON mapper.
     */
    private fun getPropertyName(raw: Class<*>, property: KProperty1<out Any, *>): String? =
        names.find(raw, property.javaGetter) ?: names.find(raw, property.javaField)

    private fun removeUnwritten(raw: Class<*>, schema: Schema<*>, properties: MutableMap<String, Schema<Any>>) {
        val serialized = names.of(raw)

        val unwritten = properties.keys.filterNot { serialized.isWritten(it) }

        for (key in unwritten) {
            properties.remove(key)
            schema.required?.remove(key)
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
