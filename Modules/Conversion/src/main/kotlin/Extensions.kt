@file:JvmName("Extensions")

package org.genhttp.modules.conversion

import org.genhttp.api.content.ProviderException
import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.ResponseStatus
import org.genhttp.modules.conversion.formatters.FormatterRegistry

import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.time.Duration

/**
 * Attempts to convert the given memory value to the specified type.
 *
 * If the value is null or empty, nullable types and strings are converted to null, primitive types
 * (numbers, booleans and characters) are converted to their default value (such as `0` or `false`).
 * Other types are converted to null as well.
 *
 * @param type The target type to convert the value to
 * @param formatters The formatting to be used to actually perform the conversion
 * @return The converted value
 * @throws ProviderException If the value could not be converted
 */
@JvmName("convertNullableTo")
public fun ByteString?.convertTo(type: KType, formatters: FormatterRegistry): Any? {
    if (this == null || isEmpty) {
        return emptyValue(type.classifier as? KClass<*>, type.isMarkedNullable)
    }

    return convertTo(type, formatters)
}

/**
 * Attempts to convert the given memory value to the specified type.
 *
 * @param type The target type to convert the value to
 * @param formatters The formatting to be used to actually perform the conversion
 * @return The converted value
 * @throws ProviderException If the value could not be converted
 */
public fun ByteString.convertTo(type: KType, formatters: FormatterRegistry): Any? {
    try {
        val actualType = type.classifier as? KClass<*>
            ?: throw IllegalArgumentException("Type '$type' cannot be converted")

        return formatters.read(this, actualType)
    } catch (e: Exception) {
        throw ProviderException(ResponseStatus.BadRequest, "Unable to convert value '$this' to type '$type'", e)
    }
}

/**
 * Attempts to convert the given memory value to the specified (non-nullable) type.
 *
 * @param type The target type to convert the value to
 * @param formatters The formatting to be used to actually perform the conversion
 * @return The converted value
 * @throws ProviderException If the value could not be converted
 */
@JvmName("convertNullableTo")
public fun ByteString?.convertTo(type: KClass<*>, formatters: FormatterRegistry): Any? {
    if (this == null || isEmpty) {
        return emptyValue(type, false)
    }

    return convertTo(type, formatters)
}

/**
 * Attempts to convert the given memory value to the specified (non-nullable) type.
 *
 * @param type The target type to convert the value to
 * @param formatters The formatting to be used to actually perform the conversion
 * @return The converted value
 * @throws ProviderException If the value could not be converted
 */
public fun ByteString.convertTo(type: KClass<*>, formatters: FormatterRegistry): Any? {
    try {
        return formatters.read(this, type)
    } catch (e: Exception) {
        throw ProviderException(ResponseStatus.BadRequest, "Unable to convert value '$this' to type '$type'", e)
    }
}

/**
 * The value to be used if there is no value to be converted (the equivalent of `Activator.CreateInstance`
 * for value types in .NET).
 */
private fun emptyValue(type: KClass<*>?, nullable: Boolean): Any? {
    if (nullable || type == null || type == String::class) {
        return null
    }

    return when (type) {
        Boolean::class -> false
        Byte::class -> 0.toByte()
        Short::class -> 0.toShort()
        Int::class -> 0
        Long::class -> 0L
        Float::class -> 0f
        Double::class -> 0.0
        Char::class -> '\u0000'
        UByte::class -> 0.toUByte()
        UShort::class -> 0.toUShort()
        UInt::class -> 0u
        ULong::class -> 0uL
        BigDecimal::class -> BigDecimal.ZERO
        BigInteger::class -> BigInteger.ZERO
        UUID::class -> UUID(0L, 0L)
        Instant::class -> Instant.parse("0001-01-01T00:00:00Z")
        LocalDate::class -> LocalDate.of(1, 1, 1)
        LocalTime::class -> LocalTime.MIDNIGHT
        Duration::class -> Duration.ZERO
        else -> if (type.java.isEnum) type.java.enumConstants.firstOrNull() else null
    }
}
