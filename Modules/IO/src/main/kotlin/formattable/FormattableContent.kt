package org.genhttp.modules.io.formattable

import org.genhttp.api.protocol.ContentType
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.api.protocol.ResponseSink
import org.genhttp.modules.io.streaming.writeFully

import java.io.OutputStream
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

    // integral numbers are written digit by digit into the sink (GenHTTP formats them into the memory of the
    // response), everything else is formatted upfront
    private val bytes: ByteArray? = if (isIntegral(value)) null else format(value).toByteArray(Charsets.UTF_8)

    private val number: Long = if (bytes == null) (value as Number).toLong() else 0

    override val length: Long? = (bytes?.size ?: lengthOf(number)).toLong()

    override val type: ContentType?
        get() = ContentType.TextPlain

    override val encoding: ByteBuffer?
        get() = null

    override suspend fun calculateChecksum(): Long? = value.hashCode().toLong()

    override suspend fun write(sink: ResponseSink) {
        val bytes = bytes

        if (bytes != null) {
            sink.writer.writeFully(ByteBuffer.wrap(bytes))
        } else {
            writeNumber(sink.stream, number)
        }
    }

    private companion object {

        private fun isIntegral(value: Any): Boolean = value is Int || value is Long || value is Short || value is Byte

        /**
         * The number of characters of the decimal representation of the given number (including the sign).
         */
        private fun lengthOf(value: Long): Int {
            // negative values cover the whole range (including Long.MIN_VALUE)
            var remaining = if (value < 0) value else -value
            var count = if (value < 0) 2 else 1

            while (remaining <= -10) {
                remaining /= 10
                count++
            }

            return count
        }

        /**
         * Writes the decimal representation of the given number, from the most significant digit on.
         */
        private fun writeNumber(target: OutputStream, value: Long) {
            if (value < 0) {
                target.write('-'.code)
            }

            var remaining = if (value < 0) value else -value
            var divisor = 1L

            while (remaining / divisor <= -10) {
                divisor *= 10
            }

            while (divisor > 0) {
                val digit = -(remaining / divisor)

                target.write('0'.code + digit.toInt())

                remaining += digit * divisor
                divisor /= 10
            }
        }

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
