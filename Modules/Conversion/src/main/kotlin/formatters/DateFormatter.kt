package org.genhttp.modules.conversion.formatters

import org.genhttp.api.protocol.ByteString
import org.genhttp.api.protocol.ResponseContent
import org.genhttp.modules.io.strings.StringContent

import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Reads and writes dates, times and durations.
 *
 * | GenHTTP                    | genhttp-kt                    | Read                                                | Written as                         |
 * |----------------------------|-------------------------------|-----------------------------------------------------|------------------------------------|
 * | `DateTime`                 | [Instant] / [LocalDateTime]   | `MM/dd/yyyy HH:mm:ss` (and ISO 8601)                | `yyyy-MM-ddTHH:mm:ss.fffffffZ`     |
 * | `DateTimeOffset`           | [OffsetDateTime]              | `MM/dd/yyyy HH:mm:ss +HH:mm` (and ISO 8601)         | `yyyy-MM-ddTHH:mm:ss.fffffff+HH:mm`|
 * | `DateOnly`                 | [LocalDate]                   | `yyyy-MM-dd`                                        | `yyyy-MM-dd`                       |
 * | `TimeOnly`                 | [LocalTime]                   | `HH:mm[:ss[.fffffff]]`                              | `HH:mm:ss.fffffff`                 |
 * | `TimeSpan`                 | [Duration]                    | `[-][d.]hh:mm:ss[.fffffff]`                         | `[-][d.]hh:mm:ss[.fffffff]`        |
 *
 * Values without an offset are interpreted as UTC. Instants are always written in UTC, local date times
 * are written without a suffix.
 */
public class DateFormatter : Formatter {

    private companion object {

        val Types: Set<KClass<*>> = setOf(
            Instant::class, OffsetDateTime::class, LocalDateTime::class,
            LocalDate::class, LocalTime::class, Duration::class
        )

        val DateTimeG = Regex("(\\d{1,2})/(\\d{1,2})/(\\d{4}) (\\d{1,2}):(\\d{2}):(\\d{2})(?: ([+-])(\\d{2}):(\\d{2}))?")

        val DateOnly = Regex("\\d{4}-\\d{2}-\\d{2}")

        val TimeOnly = Regex("(\\d{1,2}):(\\d{2})(?::(\\d{2})(?:\\.(\\d{1,7}))?)?")

        val TimeSpanC = Regex("(-)?(?:(\\d+)\\.)?(\\d{1,2}):(\\d{2}):(\\d{2})(?:\\.(\\d{1,7}))?")

        val DateTimeWriter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSS")

        val OffsetWriter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSxxx")

        val TimeWriter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSSSSSS")

        fun parseDateTime(text: String): OffsetDateTime? {
            val match = DateTimeG.matchEntire(text)

            if (match != null) {
                val (month, day, year, hour, minute, second, sign, offsetHours, offsetMinutes) = match.destructured

                val offset = if (sign.isEmpty()) {
                    ZoneOffset.UTC
                } else {
                    val total = offsetHours.toInt() * 3600 + offsetMinutes.toInt() * 60
                    ZoneOffset.ofTotalSeconds(if (sign == "-") -total else total)
                }

                return OffsetDateTime.of(
                    year.toInt(), month.toInt(), day.toInt(),
                    hour.toInt(), minute.toInt(), second.toInt(), 0, offset
                )
            }

            // round-trippable ISO 8601 (the format used for writing)
            return try {
                OffsetDateTime.parse(text)
            } catch (_: DateTimeException) {
                try {
                    LocalDateTime.parse(text).atOffset(ZoneOffset.UTC)
                } catch (_: DateTimeException) {
                    null
                }
            }
        }

        fun parseTime(text: String): LocalTime? {
            val match = TimeOnly.matchEntire(text) ?: return null

            val (hour, minute, second, fraction) = match.destructured

            val nanos = if (fraction.isEmpty()) 0 else fraction.padEnd(7, '0').toInt() * 100

            return LocalTime.of(hour.toInt(), minute.toInt(), if (second.isEmpty()) 0 else second.toInt(), nanos)
        }

        fun parseDuration(text: String): Duration? {
            val match = TimeSpanC.matchEntire(text) ?: return null

            val (sign, days, hours, minutes, seconds, fraction) = match.destructured

            val ticks = if (fraction.isEmpty()) 0L else fraction.padEnd(7, '0').toLong()

            val result = (if (days.isEmpty()) 0 else days.toLong()).days +
                    hours.toLong().hours +
                    minutes.toLong().minutes +
                    seconds.toLong().seconds +
                    (ticks * 100).nanoseconds

            return if (sign.isEmpty()) result else -result
        }

        fun formatDuration(value: Duration): String {
            if (!value.isFinite()) {
                throw IllegalArgumentException("Unsupported value $value")
            }

            val builder = StringBuilder()

            if (value.isNegative()) {
                builder.append('-')
            }

            value.absoluteValue.toComponents { days, hours, minutes, seconds, nanoseconds ->
                if (days > 0) {
                    builder.append(days).append('.')
                }

                builder.append(hours.toString().padStart(2, '0')).append(':')
                builder.append(minutes.toString().padStart(2, '0')).append(':')
                builder.append(seconds.toString().padStart(2, '0'))

                val ticks = nanoseconds / 100

                if (ticks > 0) {
                    builder.append('.').append(ticks.toString().padStart(7, '0'))
                }
            }

            return builder.toString()
        }

    }

    override fun canHandle(type: KClass<*>): Boolean = type in Types

    override fun read(value: ByteString, type: KClass<*>): Any {
        val text = value.toString()

        try {
            val result: Any? = when (type) {
                Instant::class -> parseDateTime(text)?.toInstant()
                OffsetDateTime::class -> parseDateTime(text)
                LocalDateTime::class -> parseDateTime(text)?.toLocalDateTime()
                LocalDate::class -> if (DateOnly.matches(text)) LocalDate.parse(text) else null
                LocalTime::class -> parseTime(text)
                Duration::class -> parseDuration(text)
                else -> null
            }

            if (result != null) {
                return result
            }
        } catch (_: DateTimeException) {
            // fall through to the error below
        }

        throw IllegalArgumentException("Unable to parse '$text' as ${type.simpleName}")
    }

    override fun write(value: Any, type: KClass<*>): String = when (value) {
        is Instant -> DateTimeWriter.format(value.atOffset(ZoneOffset.UTC)) + "Z"
        is OffsetDateTime -> OffsetWriter.format(value)
        is LocalDateTime -> DateTimeWriter.format(value)
        is LocalDate -> DateTimeFormatter.ISO_LOCAL_DATE.format(value)
        is LocalTime -> TimeWriter.format(value)
        is Duration -> formatDuration(value)
        else -> throw IllegalStateException("Unsupported type ${value::class}")
    }

    override fun getContent(value: Any): ResponseContent = StringContent(write(value, value::class))

}
