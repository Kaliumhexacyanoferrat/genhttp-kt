package org.genhttp.modules.mcp.schema

import org.genhttp.modules.conversion.serializers.json.JsonFormat
import org.genhttp.modules.conversion.toJavaType

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.JavaType
import tools.jackson.databind.JsonNode
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.cfg.EnumFeature
import tools.jackson.databind.json.JsonMapper

import java.lang.reflect.AnnotatedElement
import java.lang.reflect.Field
import java.lang.reflect.Member
import java.lang.reflect.Method
import java.math.BigDecimal
import java.math.BigInteger
import java.net.URI
import java.net.URL
import java.nio.charset.Charset
import java.time.Duration
import java.time.MonthDay
import java.time.Period
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAccessor
import java.util.Calendar
import java.util.Currency
import java.util.Date
import java.util.Locale
import java.util.UUID

import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.allSupertypes
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.withNullability
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaGetter

internal object McpSerialization {

    /**
     * Lenient when reading what a model sends (e.g. numbers as strings, enums in any case), compact when writing
     * (null values are omitted, enums are written as strings). Nullable members are optional when reading.
     */
    val Default: JsonMapper = JsonFormat.getDefaultOptions()
        .disable(EnumFeature.WRITE_ENUMS_USING_INDEX)
        .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .build()

    /**
     * The mapper to bind arguments with.
     *
     * Upstream adds a modifier treating nullable members as optional, which is how the Kotlin module binds
     * nullable constructor parameters anyway, so the given mapper is used as it is.
     */
    fun forRuntime(mapper: JsonMapper): JsonMapper = mapper

    /**
     * Describes how the given mapper reads and writes the given type (`JsonTypeInfo` upstream).
     */
    fun getTypeInfo(mapper: JsonMapper, type: KType): TypeInfo {
        val classifier = type.classifier as? KClass<*> ?: return TypeInfo(type, TypeKind.None)

        if (isScalar(classifier)) {
            return TypeInfo(type, TypeKind.None)
        }

        val java = classifier.java

        if (java.isArray) {
            val component = java.componentType

            val element = if (component.isPrimitive) component.kotlin.createType() else type.arguments.firstOrNull()?.type ?: AnyType

            return TypeInfo(type, TypeKind.Enumerable, element)
        }

        if (classifier.isSubclassOf(Map::class)) {
            return TypeInfo(type, TypeKind.Dictionary, resolveArgument(type, Map::class, 1) ?: AnyType)
        }

        if (classifier.isSubclassOf(Iterable::class)) {
            return TypeInfo(type, TypeKind.Enumerable, resolveArgument(type, Iterable::class, 0) ?: AnyType)
        }

        return TypeInfo(type, TypeKind.Object, null, getProperties(mapper, classifier, type))
    }

    /**
     * Whether the given type is written as a plain JSON value (and not as an object with properties or an array).
     */
    fun isScalar(type: KClass<*>): Boolean {
        val java = type.java

        return java.isPrimitive || java.isEnum || Enum::class.java.isAssignableFrom(java) || type == Any::class || type == Unit::class ||
                type == ByteArray::class || type == Char::class || type == Boolean::class || type == UUID::class ||
                type == URI::class || type == URL::class || type == UByte::class || type == UShort::class || type == UInt::class || type == ULong::class ||
                type.isValue || ScalarBases.any { it.java.isAssignableFrom(java) }
    }

    private val ScalarBases: List<KClass<*>> = listOf(
        CharSequence::class, Number::class, JsonNode::class, TemporalAccessor::class,
        Date::class, Calendar::class, Duration::class, Period::class, ZoneId::class, Locale::class, Currency::class, Charset::class,
        YearMonth::class, MonthDay::class, BigInteger::class, BigDecimal::class
    )

    private val AnyType: KType = Any::class.createType(nullable = true)

    /**
     * Identifies the given type independent of its nullability (e.g. to detect recursive types).
     */
    fun keyOf(type: KType): String {
        val classifier = type.classifier

        val name = when (classifier) {
            is KClass<*> -> classifier.java.name
            is KTypeParameter -> classifier.name
            else -> classifier.toString()
        }

        if (type.arguments.isEmpty()) {
            return name
        }

        return type.arguments.joinToString(",", "$name<", ">") { argument -> argument.type?.let(::keyOf) ?: "*" }
    }

    // region Properties

    private fun getProperties(mapper: JsonMapper, classifier: KClass<*>, type: KType): List<PropertyInfo> {
        val description = try {
            mapper._serializationContext().introspectBeanDescription(mapper.constructType(type.toJavaType()))
        } catch (_: Exception) {
            return emptyList()
        }

        val isKotlin = classifier.java.isAnnotationPresent(Metadata::class.java)

        val kotlinProperties: Collection<KProperty1<out Any, *>> = if (isKotlin) runCatching { classifier.memberProperties }.getOrDefault(emptyList()) else emptyList()

        val constructorParameters: List<KParameter> = if (isKotlin) runCatching { classifier.primaryConstructor?.parameters }.getOrNull() ?: emptyList() else emptyList()

        val bindings = getBindings(classifier, type)

        val result = mutableListOf<PropertyInfo>()

        for (property in description.findProperties()) {
            if (!property.couldSerialize()) {
                continue
            }

            val accessor = property.accessor?.member ?: continue

            val kotlinProperty = kotlinProperties.firstOrNull { it.javaGetter == accessor || it.javaField == accessor }

            val parameter = kotlinProperty?.let { known -> constructorParameters.firstOrNull { it.name == known.name } }

            val propertyType = kotlinProperty?.returnType?.let { substitute(it, bindings) } ?: property.primaryType.toKType()

            val required = if (parameter != null) !parameter.isOptional && !parameter.type.isMarkedNullable else property.isRequired

            val annotations = mutableListOf<Annotation>()

            if (kotlinProperty != null) {
                annotations.addAll(runCatching { kotlinProperty.annotations }.getOrDefault(emptyList()))
            }

            for (member in listOfNotNull(property.getter?.member, property.field?.member, kotlinProperty?.javaGetter, kotlinProperty?.javaField)) {
                (member as? AnnotatedElement)?.annotations?.let { annotations.addAll(it) }
            }

            result.add(PropertyInfo(property.name, propertyType, required, annotations.distinct(), parameter?.annotations ?: emptyList(), accessor))
        }

        return result
    }

    // endregion

    // region Types

    private fun getBindings(classifier: KClass<*>, type: KType): Map<KTypeParameter, KType?> =
        classifier.typeParameters.zip(type.arguments).associate { (parameter, argument) -> parameter to argument.type }

    /**
     * Replaces the type parameters of a member type with the type arguments of the type declaring the member.
     */
    private fun substitute(type: KType, bindings: Map<KTypeParameter, KType?>): KType {
        val classifier = type.classifier

        if (classifier is KTypeParameter) {
            val bound = bindings[classifier] ?: return AnyType
            return if (type.isMarkedNullable) bound.withNullability(true) else bound
        }

        if (classifier !is KClass<*> || type.arguments.isEmpty()) {
            return type
        }

        val arguments = type.arguments.map { argument ->
            val argumentType = argument.type
            val variance = argument.variance

            if (argumentType == null || variance == null) KTypeProjection.STAR else KTypeProjection(variance, substitute(argumentType, bindings))
        }

        return runCatching { classifier.createType(arguments, type.isMarkedNullable) }.getOrDefault(type)
    }

    /**
     * Resolves the given type argument of the given supertype (e.g. the element type of a collection).
     */
    private fun resolveArgument(type: KType, supertype: KClass<*>, index: Int): KType? {
        val classifier = type.classifier as? KClass<*> ?: return null

        if (classifier == supertype) {
            return type.arguments.getOrNull(index)?.type
        }

        val declared = classifier.allSupertypes.firstOrNull { it.classifier == supertype } ?: return null

        val argument = declared.arguments.getOrNull(index)?.type ?: return null

        return substitute(argument, getBindings(classifier, type))
    }

    /**
     * Converts a type resolved by Jackson (for classes without Kotlin metadata) into a Kotlin type, treating
     * references as not nullable.
     */
    private fun JavaType.toKType(): KType {
        val raw = rawClass

        if (raw.isArray) {
            val content = contentType

            return if (content == null || content.rawClass.isPrimitive) raw.kotlin.createType() else Array::class.createType(listOf(KTypeProjection.invariant(content.toKType())))
        }

        val classifier = raw.kotlin

        val arguments = classifier.typeParameters.indices.map { index ->
            containedType(index)?.let { KTypeProjection.invariant(it.toKType()) } ?: KTypeProjection.STAR
        }

        return runCatching { classifier.createType(arguments) }.getOrDefault(AnyType)
    }

    // endregion

}

/**
 * How a type is represented in JSON.
 */
internal enum class TypeKind {

    /** A plain value (such as a string, number or enum). */
    None,

    /** An object with properties. */
    Object,

    /** An array. */
    Enumerable,

    /** An object with arbitrary keys. */
    Dictionary

}

/**
 * Describes how a mapper reads and writes a type.
 *
 * @property elementType The type of the elements (arrays) or values (dictionaries)
 * @property properties The properties of objects
 */
internal class TypeInfo(val type: KType, val kind: TypeKind, val elementType: KType? = null, val properties: List<PropertyInfo> = emptyList())

/**
 * A property written by a mapper.
 *
 * @property name The name of the property in JSON
 * @property type The type of the property
 * @property required Whether the property needs to be present when reading (a constructor parameter without default value)
 * @property annotations The annotations of the property, its getter and its field
 * @property parameterAnnotations The annotations of the constructor parameter associated with the property
 */
internal class PropertyInfo(
    val name: String,
    val type: KType,
    val required: Boolean,
    val annotations: List<Annotation>,
    val parameterAnnotations: List<Annotation>,
    private val accessor: Member
) {

    init {
        runCatching { (accessor as? java.lang.reflect.AccessibleObject)?.isAccessible = true }
    }

    /**
     * Reads the value of the property from the given instance.
     */
    fun get(target: Any): Any? = when (accessor) {
        is Method -> accessor.invoke(target)
        is Field -> accessor.get(target)
        else -> null
    }

}
