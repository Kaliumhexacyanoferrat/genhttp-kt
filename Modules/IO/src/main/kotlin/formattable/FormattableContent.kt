package org.genhttp.modules.io.formattable

import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseSink
import org.genhttp.modules.io.streaming.writeFully

import java.math.BigDecimal
import java.nio.ByteBuffer

/**
 * Response content that formats simple values (numbers, dates, identifiers, ...) as plain text using the
 * invariant culture, without requiring them to be converted into a string by the caller.
 *
 * In GenHTTP, this type exists twice (a generic one for value types and a non-generic one for
 * `IUtf8SpanFormattable`). As Kotlin does not allow a class to be overloaded by its generic arity, both
 * are represented by this class.
 *
 * @param value The value to be formatted
 */
public class FormattableContent(private val value: Any) : ResponseContent {

    private val bytes: ByteArray = format(value).toByteArray(Charsets.UTF_8)

    override val length: Long? = bytes.size.toLong()

    override val type: ContentType? = ContentType.TextPlain

    override val encoding: ByteBuffer? = null

    override suspend fun calculateChecksum(): Long? = value.hashCode().toLong()

    override suspend fun write(sink: ResponseSink) {
        sink.writer.writeFully(ByteBuffer.wrap(bytes))
    }

    private companion object {

        /**
         * Formats the value in a way that does not depend on the locale of the system and
         * that matches the formatting of floating point numbers known from GenHTTP.
         */
        private fun format(value: Any): String = when (value) {
            is Double -> formatFloatingPoint(value, value.toString())
            is Float -> formatFloatingPoint(value.toDouble(), value.toString())
            else -> value.toString()
        }

        private fun formatFloatingPoint(value: Double, text: String): String {
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

}
