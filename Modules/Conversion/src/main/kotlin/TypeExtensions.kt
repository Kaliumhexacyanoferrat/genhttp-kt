@file:JvmName("TypeExtensions")

package org.genhttp.modules.conversion

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.WildcardType

import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeProjection
import kotlin.reflect.KVariance

/**
 * Converts the given Kotlin type into the corresponding Java type (e.g. for serializers).
 *
 * In contrast to `kotlin.reflect.jvm.javaType`, this also works for types read from the signature of lambdas,
 * which kotlin-reflect cannot map to Java types. Primitive types are represented by their wrapper classes.
 */
public fun KType.toJavaType(): Type {
    val classifier = classifier as? KClass<*> ?: return Any::class.java

    val raw: Class<*> = classifier.javaObjectType

    if (arguments.isEmpty()) {
        return raw
    }

    if (raw.isArray) {
        return raw
    }

    val typeArguments = arguments.map { it.toJavaType() }.toTypedArray()

    return KotlinParameterizedType(raw, typeArguments)
}

private fun KTypeProjection.toJavaType(): Type {
    val type = type ?: return WildcardTypeImpl(arrayOf(Any::class.java), emptyArray())

    return when (variance) {
        KVariance.OUT -> WildcardTypeImpl(arrayOf(type.toJavaType()), emptyArray())
        KVariance.IN -> WildcardTypeImpl(arrayOf(Any::class.java), arrayOf(type.toJavaType()))
        else -> type.toJavaType()
    }
}

private class KotlinParameterizedType(private val raw: Class<*>, private val typeArguments: Array<Type>) : ParameterizedType {

    override fun getActualTypeArguments(): Array<Type> = typeArguments.copyOf()

    override fun getRawType(): Type = raw

    override fun getOwnerType(): Type? = raw.declaringClass

    override fun equals(other: Any?): Boolean =
        other is ParameterizedType && other.rawType == raw && other.actualTypeArguments.contentEquals(typeArguments) && other.ownerType == ownerType

    override fun hashCode(): Int = raw.hashCode() xor typeArguments.contentHashCode()

    override fun toString(): String = "${raw.typeName}<${typeArguments.joinToString { it.typeName }}>"

}

private class WildcardTypeImpl(private val upper: Array<Type>, private val lower: Array<Type>) : WildcardType {

    override fun getUpperBounds(): Array<Type> = upper.copyOf()

    override fun getLowerBounds(): Array<Type> = lower.copyOf()

    override fun equals(other: Any?): Boolean =
        other is WildcardType && other.upperBounds.contentEquals(upper) && other.lowerBounds.contentEquals(lower)

    override fun hashCode(): Int = upper.contentHashCode() xor lower.contentHashCode()

    override fun toString(): String = if (lower.isNotEmpty()) "? super ${lower[0].typeName}" else "? extends ${upper[0].typeName}"

}
