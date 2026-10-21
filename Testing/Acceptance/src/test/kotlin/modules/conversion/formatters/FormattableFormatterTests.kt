package org.genhttp.testing.acceptance.modules.conversion.formatters

import org.genhttp.api.protocol.ByteString
import org.genhttp.modules.conversion.formatters.FormattableFormatter

import org.junit.jupiter.api.Test

import java.math.BigDecimal
import java.util.UUID

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FormattableFormatterTests {

    private val formatter = FormattableFormatter()

    private fun bytes(s: String): ByteString = ByteString(s)

    @Test
    fun handlesPrimitiveTypes() {
        assertTrue(formatter.canHandle(Int::class))
        assertTrue(formatter.canHandle(Long::class))
        assertTrue(formatter.canHandle(Short::class))
        assertTrue(formatter.canHandle(UByte::class))
        assertTrue(formatter.canHandle(Double::class))
        assertTrue(formatter.canHandle(Float::class))
        assertTrue(formatter.canHandle(UUID::class))
        assertTrue(formatter.canHandle(BigDecimal::class))
    }

    @Test
    fun doesNotHandleNonIUtf8SpanFormattables() {
        assertFalse(formatter.canHandle(String::class))
        assertFalse(formatter.canHandle(Boolean::class))
        assertFalse(formatter.canHandle(Char::class))
    }

    @Test
    fun readsInt() = assertEquals(42, formatter.read(bytes("42"), Int::class))

    @Test
    fun readsNegativeInt() = assertEquals(-100, formatter.read(bytes("-100"), Int::class))

    @Test
    fun throwsForInvalidInt() {
        assertFailsWith<NumberFormatException> { formatter.read(bytes("abc"), Int::class) }
    }

    @Test
    fun readsLong() = assertEquals(1_000_000_000_000L, formatter.read(bytes("1000000000000"), Long::class))

    @Test
    fun readsShort() = assertEquals(32767.toShort(), formatter.read(bytes("32767"), Short::class))

    @Test
    fun readsByte() = assertEquals(255.toUByte(), formatter.read(bytes("255"), UByte::class))

    @Test
    fun readsDouble() = assertEquals(3.14, formatter.read(bytes("3.14"), Double::class) as Double, 1e-10)

    @Test
    fun readsFloat() = assertEquals(1.5f, formatter.read(bytes("1.5"), Float::class) as Float, 1e-6f)

    @Test
    fun readsUInt() = assertEquals(4294967295u, formatter.read(bytes("4294967295"), UInt::class))

    @Test
    fun readsULong() = assertEquals(18446744073709551615uL, formatter.read(bytes("18446744073709551615"), ULong::class))

    @Test
    fun readsDecimal() = assertEquals(BigDecimal("3.14"), formatter.read(bytes("3.14"), BigDecimal::class) as BigDecimal)

    @Test
    fun readsGuid() {
        val guid = UUID.fromString("d4a6b7c8-1234-5678-abcd-ef0123456789")
        assertEquals(guid, formatter.read(bytes("d4a6b7c8-1234-5678-abcd-ef0123456789"), UUID::class))
    }

    @Test
    fun writesInt() = assertEquals("42", formatter.write(42, Int::class))

    @Test
    fun writesDoubleInInvariantCulture() = assertEquals("3.14", formatter.write(3.14, Double::class))

    @Test
    fun writesDecimalInInvariantCulture() = assertEquals("3.14", formatter.write(BigDecimal("3.14"), BigDecimal::class))

    @Test
    fun writesGuid() {
        val guid = UUID.fromString("d4a6b7c8-1234-5678-abcd-ef0123456789")
        assertEquals("d4a6b7c8-1234-5678-abcd-ef0123456789", formatter.write(guid, UUID::class))
    }

}
