package org.genhttp.modules.mcp.schema

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonPropertyDescription

import org.genhttp.api.content.documentation.Summary
import org.genhttp.modules.mcp.ToolResult

import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.JsonNodeFactory
import tools.jackson.databind.node.ObjectNode

import java.math.BigDecimal
import java.math.BigInteger
import java.net.URI
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.OffsetTime
import java.time.ZonedDateTime
import java.util.Calendar
import java.util.Date
import java.util.UUID

import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.primaryConstructor

/**
 * Describes the arguments and results of tools as JSON schemas.
 *
 * Upstream generates the schemas via `JsonSchemaExporter` of System.Text.Json and adjusts them. This module
 * generates the same keywords from the Kotlin types (generics, nullability, enums, collections, maps, primitives,
 * `java.time` types) and the properties written by the mapper.
 */
internal object SchemaGenerator {

    private val Nodes: JsonNodeFactory = JsonNodeFactory.instance

    // region Functionality

    /**
     * Describes the arguments of a tool implemented by a function, one property per parameter.
     */
    fun arguments(arguments: Iterable<ToolArgument>, mapper: JsonMapper): ObjectNode {
        val properties = Nodes.objectNode()
        val required = mutableListOf<String>()

        for (argument in arguments) {
            val schema = Exporter(mapper).export(argument.type)

            val description = findDescription(argument.parameter.annotations)

            if (description != null) {
                schema.remove("description")
                insertFirst(schema, "description", description)
            }

            // the default values of parameters cannot be read via kotlin-reflect, so no "default" keyword is written

            Constraints.apply(schema, Constraints.find(argument.parameter))

            relaxNullables(schema)

            properties.set(argument.name, schema)

            if (argument.required) {
                required.add(argument.name)
            }
        }

        val result = Nodes.objectNode()

        result.put("type", "object")
        result.set("properties", properties)

        if (required.isNotEmpty()) {
            result.set("required", Nodes.arrayNode().apply { required.forEach { add(it) } })
        }

        if (properties.isEmpty) {
            result.put("additionalProperties", false)
        }

        return result
    }

    /**
     * Returns the description declared by the given annotations ([Summary], [JsonPropertyDescription] or
     * [JsonClassDescription]), if any.
     */
    fun findDescription(annotations: List<Annotation>?): String? {
        if (annotations == null) {
            return null
        }

        for (annotation in annotations) {
            when (annotation) {
                is JsonPropertyDescription -> return annotation.value
                is JsonClassDescription -> return annotation.value
                is Summary -> return annotation.text
            }
        }

        return null
    }

    /**
     * Describes the result of a tool, if it is an object.
     */
    fun output(type: KType, mapper: JsonMapper): ObjectNode? {
        val classifier = type.classifier as? KClass<*>

        if (classifier == String::class || (classifier != null && classifier.isSubclassOf(ToolResult::class))) {
            return null
        }

        val schema = Exporter(mapper).export(type)

        if (isObject(schema) && schema.has("properties")) {
            schema.put("type", "object")

            // null values are not written, so they cannot be required
            relaxNullables(schema)

            return schema
        }

        return null
    }

    // endregion

    // region Helpers

    private fun isObject(schema: ObjectNode): Boolean {
        val type = schema.get("type") ?: return false

        if (type.isString) {
            return type.stringValue() == "object"
        }

        if (type is ArrayNode) {
            var hasObject = false

            for (entry in type) {
                val name = if (entry.isString) entry.stringValue() else null

                if (name == "object") {
                    hasObject = true
                } else if (name != "null") {
                    return false
                }
            }

            return hasObject
        }

        return false
    }

    private fun relaxNullables(node: JsonNode?) {
        if (node is ArrayNode) {
            for (entry in node) {
                relaxNullables(entry)
            }
        } else if (node is ObjectNode) {
            val properties = node.get("properties")
            val required = node.get("required")

            if (properties is ObjectNode && required is ArrayNode) {
                for (i in required.size() - 1 downTo 0) {
                    val name = required.get(i)?.takeIf { it.isString }?.stringValue()

                    val property = if (name != null) properties.get(name) else null

                    if (property is ObjectNode && isNullable(property)) {
                        required.remove(i)
                    }
                }

                if (required.isEmpty) {
                    node.remove("required")
                }
            }

            for (child in node.values()) {
                relaxNullables(child)
            }
        }
    }

    private fun isNullable(property: ObjectNode): Boolean {
        val types = property.get("type")

        if (types is ArrayNode && types.any { it.isString && it.stringValue() == "null" }) {
            return true
        }

        val values = property.get("enum")

        if (values is ArrayNode && values.any { it.isNull }) {
            return true
        }

        return false
    }

    /**
     * Adds the given property as the first property of the given object.
     */
    private fun insertFirst(node: ObjectNode, name: String, value: String) {
        val entries = node.properties().map { it.key to it.value }

        node.removeAll()
        node.put(name, value)

        for ((key, entry) in entries) {
            node.set(key, entry)
        }
    }

    // endregion

    /**
     * Generates the schema of a type (the counterpart of `JsonSchemaExporter` with the transformation applied by
     * upstream, which adds descriptions and constraints).
     *
     * Types are inlined wherever they are used, recursive types reference the schema of their ancestor via a JSON
     * pointer relative to the generated schema (e.g. `{"$ref":"#"}`).
     */
    private class Exporter(private val mapper: JsonMapper) {

        private val stack = mutableListOf<Pair<String, String>>()

        fun export(type: KType): ObjectNode = generate(type, "#", null)

        private fun generate(type: KType, pointer: String, property: PropertyInfo?): ObjectNode {
            val key = McpSerialization.keyOf(type)

            val ancestor = stack.firstOrNull { it.first == key }

            val node: ObjectNode

            if (ancestor != null) {
                node = Nodes.objectNode().put("\$ref", ancestor.second)
            } else {
                stack.add(key to pointer)

                try {
                    node = create(type, pointer)
                } finally {
                    stack.removeAt(stack.size - 1)
                }

                if (type.isMarkedNullable) {
                    makeNullable(node)
                }
            }

            return describe(node, type, property)
        }

        private fun create(type: KType, pointer: String): ObjectNode {
            val classifier = type.classifier as? KClass<*> ?: return Nodes.objectNode()

            val known = createKnown(classifier)

            if (known != null) {
                return known
            }

            if (classifier.isValue) {
                val underlying = classifier.primaryConstructor?.parameters?.firstOrNull()?.type

                return if (underlying != null) create(underlying, pointer) else Nodes.objectNode()
            }

            if (Enum::class.java.isAssignableFrom(classifier.java)) {
                return createEnum(classifier)
            }

            if (McpSerialization.isScalar(classifier)) {
                return Nodes.objectNode()
            }

            val typeInfo = McpSerialization.getTypeInfo(mapper, type)

            return when (typeInfo.kind) {
                TypeKind.Enumerable -> {
                    val items = generate(typeInfo.elementType ?: AnyType, "$pointer/items", null)

                    Nodes.objectNode().put("type", "array").set("items", items)
                }

                TypeKind.Dictionary -> {
                    val values = generate(typeInfo.elementType ?: AnyType, "$pointer/additionalProperties", null)

                    Nodes.objectNode().put("type", "object").set("additionalProperties", values)
                }

                TypeKind.Object -> createObject(typeInfo, pointer)

                TypeKind.None -> Nodes.objectNode()
            }
        }

        private fun createObject(typeInfo: TypeInfo, pointer: String): ObjectNode {
            val properties = Nodes.objectNode()
            val required = Nodes.arrayNode()

            for (property in typeInfo.properties) {
                properties.set(property.name, generate(property.type, "$pointer/properties/${escape(property.name)}", property))

                if (property.required) {
                    required.add(property.name)
                }
            }

            val node = Nodes.objectNode()

            node.put("type", "object")
            node.set("properties", properties)

            if (!required.isEmpty) {
                node.set("required", required)
            }

            return node
        }

        private fun createEnum(classifier: KClass<*>): ObjectNode {
            val values = classifier.java.enumConstants?.map { mapper.valueToTree<JsonNode>(it) } ?: emptyList()

            if (values.isNotEmpty() && values.all { it.isIntegralNumber }) {
                return Nodes.objectNode().put("type", "integer")
            }

            val array = Nodes.arrayNode()

            values.forEach { array.add(it) }

            return Nodes.objectNode().set("enum", array)
        }

        private fun createKnown(classifier: KClass<*>): ObjectNode? = when (classifier) {
            String::class -> simple("string")
            Char::class -> simple("string").put("minLength", 1).put("maxLength", 1)
            Boolean::class -> simple("boolean")
            Byte::class, Short::class, Int::class, Long::class, UByte::class, UShort::class, UInt::class, ULong::class, BigInteger::class -> simple("integer")
            Float::class, Double::class, BigDecimal::class -> simple("number")
            UUID::class -> simple("string").put("format", "uuid")
            URI::class, URL::class -> simple("string").put("format", "uri")
            Instant::class, OffsetDateTime::class, ZonedDateTime::class, LocalDateTime::class, Date::class, Calendar::class -> simple("string").put("format", "date-time")
            LocalDate::class -> simple("string").put("format", "date")
            LocalTime::class, OffsetTime::class -> simple("string").put("format", "time")
            java.time.Duration::class -> simple("string").put("format", "duration")
            ByteArray::class -> simple("string")
            else -> when {
                classifier.isSubclassOf(CharSequence::class) -> simple("string")
                else -> null
            }
        }

        private fun simple(type: String): ObjectNode = Nodes.objectNode().put("type", type)

        private fun makeNullable(node: ObjectNode) {
            val type = node.get("type")

            if (type != null && type.isString) {
                node.set("type", Nodes.arrayNode().add(type.stringValue()).add("null"))
            } else if (type is ArrayNode) {
                if (type.none { it.isString && it.stringValue() == "null" }) {
                    type.add("null")
                }
            } else {
                val values = node.get("enum")

                if (values is ArrayNode && values.none { it.isNull }) {
                    values.addNull()
                }
            }
        }

        /**
         * Adds the constraints and the description of the property the schema is generated for (or of the type,
         * if the schema is not generated for a property).
         */
        private fun describe(node: ObjectNode, type: KType, property: PropertyInfo?): ObjectNode {
            if (property != null) {
                Constraints.apply(node, Constraints.find(property.annotations, property.parameterAnnotations))
            }

            if (!node.has("description")) {
                val description = findDescription(property?.annotations)
                    ?: findDescription(property?.parameterAnnotations)
                    ?: if (property == null) findDescription((type.classifier as? KClass<*>)?.java?.annotations?.toList()) else null

                if (description != null) {
                    insertFirst(node, "description", description)
                }
            }

            return node
        }

        private fun escape(name: String): String = name.replace("~", "~0").replace("/", "~1")

        private companion object {

            val AnyType: KType = Any::class.createType(nullable = true)

        }

    }

}
