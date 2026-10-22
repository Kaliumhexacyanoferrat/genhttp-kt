package org.genhttp.testing.acceptance.modules.conversion.formatters

import org.genhttp.api.protocol.ByteString
import org.genhttp.modules.conversion.formatters.DateFormatter

import org.junit.jupiter.api.Test

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class DateFormatterTests {

    private val formatter = DateFormatter()

    private fun bytes(s: String): ByteString = ByteString(s.toByteArray(Charsets.UTF_8))

    // region CanHandle

    @Test
    fun handlesDateTypes() {
        assertTrue(formatter.canHandle(Instant::class))
        assertTrue(formatter.canHandle(OffsetDateTime::class))
        assertTrue(formatter.canHandle(LocalDate::class))
        assertTrue(formatter.canHandle(LocalTime::class))
        assertTrue(formatter.canHandle(Duration::class))
    }

    @Test
    fun doesNotHandleOtherTypes() {
        assertFalse(formatter.canHandle(Int::class))
        assertFalse(formatter.canHandle(String::class))
        assertFalse(formatter.canHandle(Boolean::class))
    }

    // endregion

    // region Read

    @Test
    fun readsDateTime() {
        val result = formatter.read(bytes("01/15/2024 10:30:00"), Instant::class) as Instant
        assertEquals(Instant.parse("2024-01-15T10:30:00Z"), result)
    }

    @Test
    fun readsDateTimeOffset() {
        val result = formatter.read(bytes("01/15/2024 10:30:00 +00:00"), OffsetDateTime::class) as OffsetDateTime
        assertEquals(OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC), result)
    }

    @Test
    fun readsDateOnly() {
        val result = formatter.read(bytes("2024-01-15"), LocalDate::class) as LocalDate
        assertEquals(LocalDate.of(2024, 1, 15), result)
    }

    @Test
    fun readsTimeOnly() {
        val result = formatter.read(bytes("10:30:00"), LocalTime::class) as LocalTime
        assertEquals(LocalTime.of(10, 30, 0), result)
    }

    @Test
    fun readsTimeSpan() {
        val result = formatter.read(bytes("01:30:00"), Duration::class) as Duration
        assertEquals(1.hours + 30.minutes, result)
    }

    @Test
    fun throwsForInvalidDateTime() {
        assertFailsWith<IllegalArgumentException> { formatter.read(bytes("not-a-date"), Instant::class) }
    }

    @Test
    fun throwsForInvalidDateOnly() {
        assertFailsWith<IllegalArgumentException> { formatter.read(bytes("15-01-2024"), LocalDate::class) }
    }

    @Test
    fun throwsForInvalidTimeSpan() {
        assertFailsWith<IllegalArgumentException> { formatter.read(bytes("not-a-timespan"), Duration::class) }
    }

    // endregion

    // region Write

    @Test
    fun writesDateTime() {
        val value = Instant.parse("2024-01-15T10:30:00Z")
        assertEquals("2024-01-15T10:30:00.0000000Z", formatter.write(value, Instant::class))
    }

    @Test
    fun writesDateTimeOffset() {
        val value = OffsetDateTime.of(2024, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC)
        assertEquals("2024-01-15T10:30:00.0000000+00:00", formatter.write(value, OffsetDateTime::class))
    }

    @Test
    fun writesDateOnly() {
        val value = LocalDate.of(2024, 1, 15)
        assertEquals("2024-01-15", formatter.write(value, LocalDate::class))
    }

    @Test
    fun writesTimeOnly() {
        val value = LocalTime.of(10, 30, 0)
        assertEquals("10:30:00.0000000", formatter.write(value, LocalTime::class))
    }

    @Test
    fun writesTimeSpan() {
        val value = 1.hours + 30.minutes
        assertEquals("01:30:00", formatter.write(value, Duration::class))
    }

    @Test
    fun writesDateOnlyRoundTrip() {
        val original = LocalDate.of(2024, 6, 10)
        val written = formatter.write(original, LocalDate::class)
        val readBack = formatter.read(bytes(written), LocalDate::class) as LocalDate
        assertEquals(original, readBack)
    }

    @Test
    fun writesTimeOnlyRoundTrip() {
        val original = LocalTime.of(14, 5, 59)
        val written = formatter.write(original, LocalTime::class)
        val readBack = formatter.read(bytes(written), LocalTime::class) as LocalTime
        assertEquals(original, readBack)
    }

    @Test
    fun writesTimeSpanRoundTrip() {
        val original = 2.hours + 15.minutes + 30.seconds
        val written = formatter.write(original, Duration::class)
        val readBack = formatter.read(bytes(written), Duration::class) as Duration
        assertEquals(original, readBack)
    }

    // endregion

}
