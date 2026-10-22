package org.genhttp.modules.conversion.formatters

import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.modules.io.formattable.FormattableContent

import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID

import kotlin.reflect.KClass

/**
 * Reads and writes numbers (including the unsigned types), [BigDecimal], [BigInteger] and [UUID]
 * using the invariant culture (a dot as the decimal separator and no grouping).
 *
 * GenHTTP supports all types implementing `IUtf8SpanFormattable` (except for `char`). As there is no such
 * interface on the JVM, this formatter handles the types listed above, which are the ones GenHTTP
 * is able to read.
 */
public class FormattableFormatter : Formatter {

    private companion object {

        val Types: Set<KClass<*>> = setOf(
            Byte::class, Short::class, Int::class, Long::class,
            UByte::class, UShort::class, UInt::class, ULong::class,
            Float::class, Double::class,
            BigDecimal::class, BigInteger::class,
            UUID::class
        )

        // the JVM classes of the types (primitive and boxed), as comparing classes is cheaper than comparing KClasses
        val JavaTypes: Set<Class<*>> = Types.flatMapTo(HashSet()) { listOfNotNull(it.javaPrimitiveType, it.javaObjectType) }

        val IntTypes: Set<Class<*>> = setOf(Int::class.javaPrimitiveType!!, Int::class.javaObjectType)

        val LongTypes: Set<Class<*>> = setOf(Long::class.javaPrimitiveType!!, Long::class.javaObjectType)

        /**
         * Parses a plain decimal number (optional sign, at most 18 digits, surrounded by whitespace) directly from
         * the given bytes. Returns null for everything else, which is then handled (or rejected) by the regular
         * parser, so that the behavior does not change.
         */
        fun parsePlainNumber(value: ByteString): Long? {
            val bytes = value.bytes

            var start = bytes.position()
            var end = bytes.limit()

            while (start < end && isWhitespace(bytes.get(start))) start++
            while (end > start && isWhitespace(bytes.get(end - 1))) end--

            var negative = false

            if (start < end) {
                val sign = bytes.get(start)

                if (sign == '-'.code.toByte() || sign == '+'.code.toByte()) {
                    negative = sign == '-'.code.toByte()
                    start++
                }
            }

            val digits = end - start

            if (digits == 0 || digits > 18) {
                return null
            }

            var result = 0L

            for (i in start until end) {
                val digit = bytes.get(i) - '0'.code.toByte()

                if (digit !in 0..9) {
                    return null
                }

                result = result * 10 + digit
            }

            return if (negative) -result else result
        }

        // the characters removed by trim() that can be part of a byte string
        fun isWhitespace(value: Byte): Boolean = value == ' '.code.toByte() || value in 0x09..0x0D || value in 0x1C..0x1F

        val FloatingPoint = Regex("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?")

        val Decimal = Regex("[+-]?(\\d+(\\.\\d*)?|\\.\\d+)")

        val Integer = Regex("[+-]?\\d+")

        val GuidD = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")

        val GuidN = Regex("[0-9a-fA-F]{32}")

        fun parseFloatingPoint(text: String): String {
            val lower = text.lowercase()

            if (lower == "nan" || lower == "infinity" || lower == "+infinity" || lower == "-infinity") {
                return when (lower) {
                    "nan" -> "NaN"
                    "-infinity" -> "-Infinity"
                    else -> "Infinity"
                }
            }

            if (!FloatingPoint.matches(text)) {
                throw NumberFormatException("The input string '$text' was not in a correct format")
            }

            return text
        }

        fun parseGuid(text: String): UUID {
            var raw = text

            if ((raw.startsWith("{") && raw.endsWith("}")) || (raw.startsWith("(") && raw.endsWith(")"))) {
                raw = raw.substring(1, raw.length - 1)
            }

            if (GuidD.matches(raw)) {
                return UUID.fromString(raw)
            }

            if (GuidN.matches(raw)) {
                return UUID.fromString(
                    "${raw.substring(0, 8)}-${raw.substring(8, 12)}-${raw.substring(12, 16)}-${raw.substring(16, 20)}-${raw.substring(20)}"
                )
            }

            throw NumberFormatException("Unrecognized Guid format: '$text'")
        }

        fun format(value: Any): String = when (value) {
            is Double -> formatFloatingPoint(value, value.toString())
            is Float -> formatFloatingPoint(value.toDouble(), value.toString())
            is BigDecimal -> value.toPlainString()
            else -> value.toString()
        }

        // formats like .NET does (shortest round-trippable representation, no trailing ".0")
        fun formatFloatingPoint(value: Double, text: String): String {
            if (value.isNaN()) {
                return "NaN"
            }

            if (value.isInfinite()) {
                return if (value > 0) "Infinity" else "-Infinity"
            }

            if (value == 0.0) {
                return if (1.0 / value < 0) "-0" else "0"
            }

            val decimal = BigDecimal(text).stripTrailingZeros()

            val exponent = decimal.precision() - decimal.scale() - 1

            if (exponent in -5 until 15) {
                return decimal.toPlainString()
            }

            val digits = decimal.unscaledValue().abs().toString()
            val sign = if (decimal.signum() < 0) "-" else ""

            val mantissa = if (digits.length > 1) "${digits[0]}.${digits.substring(1)}" else digits
            val suffix = (if (exponent < 0) "-" else "+") + Math.abs(exponent).toString().padStart(2, '0')

            return "$sign${mantissa}E$suffix"
        }

    }

    override fun canHandle(type: KClass<*>): Boolean = type.java in JavaTypes

    override fun read(value: ByteString, type: KClass<*>): Any {
        val javaType = type.java

        if (javaType in IntTypes) {
            val number = parsePlainNumber(value)

            if (number != null && number >= Int.MIN_VALUE && number <= Int.MAX_VALUE) {
                return number.toInt()
            }
        } else if (javaType in LongTypes) {
            parsePlainNumber(value)?.let { return it }
        }

        val text = value.toString().trim()

        try {
            return when (type) {
                Int::class -> text.toInt()
                Long::class -> text.toLong()
                Short::class -> text.toShort()
                Byte::class -> text.toByte()
                UInt::class -> text.toUInt()
                ULong::class -> text.toULong()
                UShort::class -> text.toUShort()
                UByte::class -> text.toUByte()
                Float::class -> parseFloatingPoint(text).toFloat()
                Double::class -> parseFloatingPoint(text).toDouble()
                BigDecimal::class -> if (Decimal.matches(text)) BigDecimal(text) else throw NumberFormatException()
                BigInteger::class -> if (Integer.matches(text)) BigInteger(text) else throw NumberFormatException()
                UUID::class -> parseGuid(text)
                else -> throw IllegalArgumentException("Invalid value '$text' for primitive type ${type.simpleName}")
            }
        } catch (e: NumberFormatException) {
            throw NumberFormatException("Invalid value '$text' for primitive type ${type.simpleName}")
        }
    }

    override fun write(value: Any, type: KClass<*>): String = format(value)

    override fun getContent(value: Any): ResponseContent {
        if (value.javaClass in JavaTypes) {
            return FormattableContent(value)
        }

        throw UnsupportedOperationException()
    }

}
